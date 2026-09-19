# asyncmemo

A local Python asynchronous memoization library. No external dependencies or services.
The existing implementation supports serial cache hits, expiry and invalidation;
its handling of concurrent calls, cancellation and invalidation during a load needs repair.

```python
from asyncmemo import AsyncMemo

async def loader(key):
    return {"key": key}

# Use within a single asyncio event loop; supply a monotonic clock.
memo = AsyncMemo(loader, ttl=10, clock=clock)
value = await memo.get("document")
memo.invalidate("document")
```

Public API: `AsyncMemo(loader, *, ttl, clock)`, `await memo.get(key)`,
`memo.invalidate(key) -> None`. Inputs are valid strings, an async loader,
a positive finite TTL and a nondecreasing clock. Values may include `None`.

Run the existing regression tests from this directory:

```sh
python3 -m unittest discover -s tests -v
```
