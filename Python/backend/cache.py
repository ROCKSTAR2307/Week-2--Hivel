# cache.py
import redis.asyncio as redis

# Single global connection pool
r = redis.from_url("redis://localhost:6379", decode_responses=True)

async def get_redis():
    return r
