import unittest

from asyncmemo import AsyncMemo


class SerialTests(unittest.IsolatedAsyncioTestCase):
    async def test_hit_and_exact_expiry(self):
        now = [0.0]
        calls = []

        async def load(key):
            result = object()
            calls.append(result)
            return result

        memo = AsyncMemo(load, ttl=10, clock=lambda: now[0])
        first = await memo.get("x")
        self.assertIs(await memo.get("x"), first)
        now[0] = 10.0
        self.assertIsNot(await memo.get("x"), first)
        self.assertEqual(len(calls), 2)

    async def test_none_and_invalidation(self):
        calls = []

        async def load(key):
            calls.append(key)
            return None

        memo = AsyncMemo(load, ttl=2, clock=lambda: 0)
        self.assertIsNone(await memo.get("x"))
        self.assertIsNone(await memo.get("x"))
        self.assertEqual(calls, ["x"])
        self.assertIsNone(memo.invalidate("missing"))
        self.assertIsNone(memo.invalidate("x"))
        await memo.get("x")
        self.assertEqual(calls, ["x", "x"])

    async def test_error_is_not_cached(self):
        error = LookupError("offline fixture")
        calls = []

        async def load(key):
            calls.append(key)
            if len(calls) == 1:
                raise error
            return 7

        memo = AsyncMemo(load, ttl=2, clock=lambda: 0)
        with self.assertRaises(LookupError) as caught:
            await memo.get("x")
        self.assertIs(caught.exception, error)
        self.assertEqual(await memo.get("x"), 7)


if __name__ == "__main__":
    unittest.main()
