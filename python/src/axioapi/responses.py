import json
from typing import Any, Dict, Optional

from .errors import (AuthenticationError, AxioAPIError, InsufficientCreditsError, NotFoundError,
                     RateLimitError, ValidationError)
from .transport import HttpResponse


def parse_success(response: HttpResponse, raw: bool) -> Any:
    """Returns `data`, the whole envelope when raw, or bytes for non-JSON bodies (files)."""
    if "json" not in (response.header("Content-Type") or ""):
        return response.body
    if not response.body:
        return None
    envelope = json.loads(response.body.decode("utf-8"))
    if raw or not isinstance(envelope, dict):
        return envelope
    return envelope.get("data")


def build_error(response: HttpResponse) -> AxioAPIError:
    body = _json_or_empty(response.body)
    detail = body.get("error") if isinstance(body.get("error"), dict) else {}
    fields = dict(
        message=detail.get("message") or body.get("message") or f"HTTP {response.status}",
        status=response.status,
        code=detail.get("code"),
        request_id=detail.get("request_id") or response.header("X-Request-Id"),
        fields=detail.get("fields"),
        body=body or None,
    )
    if response.status == 429:
        return RateLimitError(retry_after=_retry_after_seconds(response), **fields)
    error_class = _ERROR_BY_STATUS.get(response.status, AxioAPIError)
    return error_class(**fields)


_ERROR_BY_STATUS = {
    401: AuthenticationError,
    402: InsufficientCreditsError,
    404: NotFoundError,
    422: ValidationError,
}


def _json_or_empty(content: bytes) -> Dict[str, Any]:
    try:
        parsed = json.loads(content.decode("utf-8"))
    except ValueError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _retry_after_seconds(response: HttpResponse) -> Optional[float]:
    value = response.header("Retry-After")
    return float(value) if value and value.isdigit() else None
