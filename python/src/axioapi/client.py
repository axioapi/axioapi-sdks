"""AxioAPI client. Standard library only."""

from __future__ import annotations

import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from importlib import resources
from typing import Any, Callable, Dict, Optional

from .errors import (APIConnectionError, AuthenticationError, AxioAPIError, InsufficientCreditsError,
                     NotFoundError, RateLimitError, ValidationError)

DEFAULT_BASE_URL = "https://axioapi.com"
VERSION = "1.0.0"
_RETRY_STATUS = {502, 503, 504}


def _load_operations() -> Dict[str, dict]:
    text = resources.files("axioapi").joinpath("operations.json").read_text(encoding="utf-8")
    return json.loads(text)["operations"]


def _normalize(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


class _Group:
    """`client.seo`: attribute access to every operation of one API group."""

    def __init__(self, client: "AxioAPI", group: str):
        self._client = client
        self._group = group

    def __getattr__(self, name: str) -> Callable[..., Any]:
        key = self._client._resolve(self._group, name)
        if key is None:
            raise AttributeError(f"AxioAPI has no operation '{self._group}.{name}'")
        return lambda **params: self._client.call(key, **params)

    def __dir__(self):
        return sorted(_normalize(k.partition(".")[2]) for k in self._client._operations
                      if _normalize(k.partition(".")[0]) == self._group)


class AxioAPI:
    """Client for https://axioapi.com. Create an API key in your account and pass it here or set AXIOAPI_KEY.

        client = AxioAPI("ak_...")
        client.seo.keyword_metrics(keywords=["api gateway"], country="us")
    """

    def __init__(self, api_key: Optional[str] = None, *, base_url: Optional[str] = None, timeout: float = 30.0,
                 max_retries: int = 2, user_agent: Optional[str] = None,
                 sleep: Callable[[float], None] = time.sleep):
        self.api_key = api_key or os.environ.get("AXIOAPI_KEY")
        if not self.api_key:
            raise ValueError("Pass api_key or set the AXIOAPI_KEY environment variable.")
        self.base_url = (base_url or os.environ.get("AXIOAPI_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
        self.timeout = timeout
        self.max_retries = max(0, max_retries)
        self.user_agent = user_agent or f"axioapi-python/{VERSION}"
        self._sleep = sleep
        self._operations = _load_operations()
        self._index: Dict[str, str] = {}
        for key in self._operations:
            group, _, rest = key.partition(".")
            self._index[f"{_normalize(group)}.{_normalize(rest)}"] = key
        self._groups = {_normalize(k.partition(".")[0]) for k in self._operations}

    # -- operation helpers -------------------------------------------------

    def operations(self) -> Dict[str, dict]:
        """Registry of every operation: method, path, parameters and credit cost."""
        return dict(self._operations)

    def _resolve(self, group: str, name: str) -> Optional[str]:
        return self._index.get(f"{_normalize(group)}.{_normalize(name)}")

    def __getattr__(self, name: str) -> _Group:
        if name.startswith("_") or name not in self.__dict__.get("_groups", ()):
            raise AttributeError(name)
        return _Group(self, name)

    def call(self, operation: str, **params: Any) -> Any:
        """Call an operation by capability key, e.g. call("seo.keyword-metrics", keywords=[...]). Returns `data`."""
        key = operation if operation in self._operations else None
        if key is None:
            group, _, rest = operation.partition(".")
            key = self._index.get(f"{_normalize(group)}.{_normalize(rest)}")
        if key is None:
            raise ValueError(f"Unknown operation '{operation}'. See client.operations().")
        op = self._operations[key]
        path = op["path"]
        params = dict(params)
        for name in op["path_params"]:
            if name not in params:
                raise ValueError(f"Missing path parameter '{name}' for {key}")
            path = path.replace("{" + name + "}", urllib.parse.quote(str(params.pop(name)), safe=""))
        has_body = op["method"] not in ("GET", "DELETE", "HEAD")
        body_names = set(op["body"])
        query: Dict[str, Any] = {}
        body: Dict[str, Any] = {}
        for name, value in params.items():
            if value is None:
                continue
            if has_body and (name in body_names or name not in op["query"]):
                body[name] = value
            else:
                query[name] = value
        return self.request(op["method"], path, params=query or None, json_body=body if has_body and body else None)

    # -- transport ---------------------------------------------------------

    def request(self, method: str, path: str, *, params: Optional[dict] = None, json_body: Any = None,
                raw: bool = False) -> Any:
        """Send a request. Returns `data`, the whole envelope with raw=True, or bytes for file downloads."""
        url = self.base_url + (path if path.startswith("/") else "/" + path)
        if params:
            url += "?" + urllib.parse.urlencode(_flatten(params))
        payload = json.dumps(json_body).encode("utf-8") if json_body is not None else None
        headers = {"Authorization": f"Bearer {self.api_key}", "Accept": "application/json", "User-Agent": self.user_agent}
        if payload is not None:
            headers["Content-Type"] = "application/json"
        method = method.upper()
        idempotent = method in ("GET", "HEAD", "DELETE")

        attempt = 0
        while True:
            try:
                req = urllib.request.Request(url, data=payload, headers=headers, method=method)
                with urllib.request.urlopen(req, timeout=self.timeout) as res:
                    return self._parse(res.headers, res.read(), raw)
            except urllib.error.HTTPError as err:
                body = err.read()
                retry = err.code == 429 or (idempotent and err.code in _RETRY_STATUS)
                if retry and attempt < self.max_retries:
                    self._sleep(_delay(err.headers.get("Retry-After"), attempt))
                    attempt += 1
                    continue
                raise self._error(err.code, err.headers, body) from None
            except (urllib.error.URLError, TimeoutError, ConnectionError) as err:
                if idempotent and attempt < self.max_retries:
                    self._sleep(_delay(None, attempt))
                    attempt += 1
                    continue
                raise APIConnectionError(f"Could not reach {self.base_url}: {getattr(err, 'reason', err)}") from None

    def _parse(self, headers: Any, content: bytes, raw: bool) -> Any:
        if "json" not in (headers.get("Content-Type") or ""):
            return content
        if not content:
            return None
        envelope = json.loads(content.decode("utf-8"))
        if raw or not isinstance(envelope, dict):
            return envelope
        return envelope.get("data")

    def _error(self, status: int, headers: Any, content: bytes) -> AxioAPIError:
        try:
            body = json.loads(content.decode("utf-8"))
        except ValueError:
            body = None
        body_dict = body if isinstance(body, dict) else {}
        err = body_dict.get("error") if isinstance(body_dict.get("error"), dict) else {}
        message = err.get("message") or body_dict.get("message") or f"HTTP {status}"
        extra = dict(message=message, status=status, code=err.get("code"),
                     request_id=err.get("request_id") or headers.get("X-Request-Id"),
                     fields=err.get("fields"), body=body)
        if status == 401:
            return AuthenticationError(**extra)
        if status == 402:
            return InsufficientCreditsError(**extra)
        if status == 404:
            return NotFoundError(**extra)
        if status == 422:
            return ValidationError(**extra)
        if status == 429:
            retry_after = headers.get("Retry-After")
            return RateLimitError(retry_after=float(retry_after) if retry_after and retry_after.isdigit() else None, **extra)
        return AxioAPIError(**extra)


def _flatten(params: dict) -> list:
    out = []
    for key, value in params.items():
        if isinstance(value, bool):
            out.append((key, "true" if value else "false"))
        elif isinstance(value, (list, tuple)):
            out.extend((f"{key}[]", str(v)) for v in value)
        elif value is not None:
            out.append((key, str(value)))
    return out


def _delay(retry_after: Optional[str], attempt: int) -> float:
    if retry_after and retry_after.replace(".", "", 1).isdigit():
        return min(float(retry_after), 30.0)
    return min(0.5 * (2 ** attempt), 8.0)
