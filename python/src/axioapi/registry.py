import json
from dataclasses import dataclass, fields
from importlib import resources
from typing import Dict, List, Optional

from .naming import normalize


@dataclass(frozen=True)
class Operation:
    key: str
    method: str
    path: str
    summary: str
    cost: Optional[float]
    path_params: List[str]
    query: List[str]
    body: List[str]
    binary: bool
    auth: bool = True

    @property
    def has_body(self) -> bool:
        return self.method not in ("GET", "DELETE", "HEAD")


class Registry:
    """Every API operation, loaded from operations.json."""

    def __init__(self, operations: Dict[str, Operation]):
        self._operations = operations
        self._index = {self._index_key(*key.partition(".")[::2]): key for key in operations}
        self._groups = {normalize(key.partition(".")[0]) for key in operations}

    @classmethod
    def load(cls) -> "Registry":
        text = resources.files("axioapi").joinpath("operations.json").read_text(encoding="utf-8")
        raw = json.loads(text)["operations"]
        known = {f.name for f in fields(Operation)}
        return cls({key: Operation(key=key, **{k: v for k, v in entry.items() if k in known}) for key, entry in raw.items()})

    @staticmethod
    def _index_key(group: str, name: str) -> str:
        return f"{normalize(group)}.{normalize(name)}"

    def all(self) -> Dict[str, Operation]:
        return dict(self._operations)

    def resolve(self, group: str, name: str) -> Optional[str]:
        return self._index.get(self._index_key(group, name))

    def find(self, operation: str) -> Optional[Operation]:
        """Looks an operation up by exact key or by any spelling of group.name."""
        if operation in self._operations:
            return self._operations[operation]
        group, _, name = operation.partition(".")
        key = self.resolve(group, name)
        return self._operations[key] if key else None

    def has_group(self, group: str) -> bool:
        return normalize(group) in self._groups

    def group_names(self, group: str) -> List[str]:
        prefix = normalize(group) + "."
        return sorted(key[len(prefix):] for key in self._index if key.startswith(prefix))
