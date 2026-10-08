import urllib.parse
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from .registry import Operation


@dataclass(frozen=True)
class PreparedRequest:
    method: str
    path: str
    query: Dict[str, Any]
    body: Optional[Dict[str, Any]]


def build_request(operation: Operation, params: Dict[str, Any]) -> PreparedRequest:
    """Splits flat params into path, query and body according to the operation."""
    remaining = dict(params)
    path = _fill_path(operation, remaining)
    query: Dict[str, Any] = {}
    body: Dict[str, Any] = {}
    for name, value in remaining.items():
        if value is None:
            continue
        target = body if _belongs_in_body(operation, name) else query
        target[name] = value
    return PreparedRequest(operation.method, path, query, body or None if operation.has_body else None)


def _fill_path(operation: Operation, params: Dict[str, Any]) -> str:
    path = operation.path
    for name in operation.path_params:
        if params.get(name) is None:
            raise ValueError(f"Missing path parameter '{name}' for {operation.key}")
        path = path.replace("{" + name + "}", urllib.parse.quote(str(params.pop(name)), safe=""))
    return path


def _belongs_in_body(operation: Operation, name: str) -> bool:
    return operation.has_body and (name in operation.body or name not in operation.query)


def encode_query(params: Dict[str, Any]) -> str:
    return urllib.parse.urlencode(_query_pairs(params))


def _query_pairs(params: Dict[str, Any]) -> List[Tuple[str, str]]:
    pairs: List[Tuple[str, str]] = []
    for key, value in params.items():
        if isinstance(value, (list, tuple)):
            pairs.extend((f"{key}[]", str(item)) for item in value)
        elif isinstance(value, bool):
            pairs.append((key, "true" if value else "false"))
        elif value is not None:
            pairs.append((key, str(value)))
    return pairs
