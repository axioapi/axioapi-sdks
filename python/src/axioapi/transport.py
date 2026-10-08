import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Dict, Mapping, Optional


class ConnectionFailure(Exception):
    """The request produced no HTTP response."""


@dataclass(frozen=True)
class HttpResponse:
    status: int
    headers: Mapping[str, str]
    body: bytes

    def header(self, name: str) -> Optional[str]:
        return self.headers.get(name)

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 300


class UrllibTransport:
    """One HTTP round trip with the standard library; HTTP error statuses are returned, not raised."""

    def __init__(self, timeout: float):
        self._timeout = timeout

    def send(self, method: str, url: str, headers: Dict[str, str], payload: Optional[bytes]) -> HttpResponse:
        request = urllib.request.Request(url, data=payload, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                return HttpResponse(response.status, response.headers, response.read())
        except urllib.error.HTTPError as error:
            return HttpResponse(error.code, error.headers, error.read())
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            raise ConnectionFailure(str(getattr(error, "reason", error))) from None
