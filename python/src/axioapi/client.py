import json
import os
import time
from typing import Any, Callable, Dict, Optional

from .errors import APIConnectionError
from .groups import OperationGroup
from .registry import Operation, Registry
from .request_builder import build_request, encode_query
from .responses import build_error, parse_success
from .retry import RetryPolicy
from .transport import ConnectionFailure, UrllibTransport

DEFAULT_BASE_URL = "https://axioapi.com"
VERSION = "1.0.0"


class AxioAPI:
    """Client for https://axioapi.com.

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
        self.user_agent = user_agent or f"axioapi-python/{VERSION}"
        self.registry = Registry.load()
        self._retry = RetryPolicy(max_retries, sleep)
        self._transport = UrllibTransport(timeout)

    def operations(self) -> Dict[str, Operation]:
        return self.registry.all()

    def __getattr__(self, name: str) -> OperationGroup:
        if name.startswith("_") or "registry" not in self.__dict__ or not self.registry.has_group(name):
            raise AttributeError(name)
        return OperationGroup(self, name)

    def call(self, operation: str, **params: Any) -> Any:
        """Calls an operation by capability key (for example "seo.keyword-metrics") and returns `data`."""
        found = self.registry.find(operation)
        if found is None:
            raise ValueError(f"Unknown operation '{operation}'. See client.operations().")
        prepared = build_request(found, params)
        return self.request(prepared.method, prepared.path, params=prepared.query or None, json_body=prepared.body)

    def request(self, method: str, path: str, *, params: Optional[dict] = None, json_body: Any = None,
                raw: bool = False) -> Any:
        """Sends a request with retries; returns `data`, the envelope with raw=True, or bytes for files."""
        method = method.upper()
        url = self._url(path, params)
        payload = json.dumps(json_body).encode("utf-8") if json_body is not None else None
        headers = self._headers(has_body=payload is not None)

        for attempt in range(self._retry.max_retries + 1):
            try:
                response = self._transport.send(method, url, headers, payload)
            except ConnectionFailure as failure:
                if self._retry.should_retry_connection(method, attempt):
                    self._retry.wait(attempt)
                    continue
                raise APIConnectionError(f"Could not reach {self.base_url}: {failure}") from None
            if response.ok:
                return parse_success(response, raw)
            if self._retry.should_retry_status(response.status, method, attempt):
                self._retry.wait(attempt, response.header("Retry-After"))
                continue
            raise build_error(response)

    def _url(self, path: str, params: Optional[dict]) -> str:
        url = self.base_url + (path if path.startswith("/") else "/" + path)
        return f"{url}?{encode_query(params)}" if params else url

    def _headers(self, has_body: bool) -> Dict[str, str]:
        headers = {"Authorization": f"Bearer {self.api_key}", "Accept": "application/json", "User-Agent": self.user_agent}
        if has_body:
            headers["Content-Type"] = "application/json"
        return headers
