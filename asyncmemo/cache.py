"""Small asynchronous cache used by an in-process metadata resolver."""
from collections.abc import Awaitable, Callable
from typing import Any


class AsyncMemo:
    def __init__(self, loader: Callable[[str], Awaitable[Any]], *,
                 ttl: float, clock: Callable[[], float]):
        self._loader = loader
        self._ttl = ttl
        self._clock = clock
        self._values: dict[str, tuple[float, Any]] = {}

    async def get(self, key: str) -> Any:
        entry = self._values.get(key)
        if entry is not None:
            expires, value = entry
            if self._clock() < expires:
                return value
        value = await self._loader(key)
        self._values[key] = (self._clock() + self._ttl, value)
        return value

    def invalidate(self, key: str) -> None:
        self._values.pop(key, None)
