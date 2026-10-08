from typing import TYPE_CHECKING, Any, Callable

if TYPE_CHECKING:
    from .client import AxioAPI


class OperationGroup:
    """`client.seo`: attribute access to the operations of one API group."""

    def __init__(self, client: "AxioAPI", group: str):
        self._client = client
        self._group = group

    def __getattr__(self, name: str) -> Callable[..., Any]:
        key = self._client.registry.resolve(self._group, name)
        if key is None:
            raise AttributeError(f"AxioAPI has no operation '{self._group}.{name}'")
        return lambda **params: self._client.call(key, **params)

    def __dir__(self):
        return self._client.registry.group_names(self._group)
