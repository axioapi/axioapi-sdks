"""Exceptions raised by the AxioAPI client."""

from __future__ import annotations

from typing import Any, Optional


class AxioAPIError(Exception):
    """Base class. Every API error carries the HTTP status, error code and request_id for support."""

    def __init__(self, message: str, *, status: Optional[int] = None, code: Optional[str] = None,
                 request_id: Optional[str] = None, fields: Optional[dict] = None, body: Any = None):
        super().__init__(message)
        self.message = message
        self.status = status
        self.code = code
        self.request_id = request_id
        self.fields = fields or {}
        self.body = body

    def __str__(self) -> str:
        parts = [self.message]
        if self.status:
            parts.append(f"status={self.status}")
        if self.code:
            parts.append(f"code={self.code}")
        if self.request_id:
            parts.append(f"request_id={self.request_id}")
        return " | ".join(parts)


class AuthenticationError(AxioAPIError):
    """401: the API key is missing or invalid."""


class InsufficientCreditsError(AxioAPIError):
    """402: not enough credits for this call."""


class NotFoundError(AxioAPIError):
    """404: the resource does not exist, expired or is not yours."""


class ValidationError(AxioAPIError):
    """422: invalid parameters. See `fields` for per-field messages."""


class RateLimitError(AxioAPIError):
    """429: request limit reached. `retry_after` is in seconds when the server sent it."""

    def __init__(self, message: str, *, retry_after: Optional[float] = None, **kwargs: Any):
        super().__init__(message, **kwargs)
        self.retry_after = retry_after


class APIConnectionError(AxioAPIError):
    """The request never produced an HTTP response (DNS, TLS, timeout)."""
