import asyncio
import redis.asyncio as redis

async def test():
    r = redis.from_url("redis://localhost:6379", decode_responses=True)
    await r.set("testkey", "rockstar")
    val = await r.get("testkey")
    print(val)

asyncio.run(test())
