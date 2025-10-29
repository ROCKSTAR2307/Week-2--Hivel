import time
import redis.asyncio as redis
from fastapi import Request, HTTPException, status

REDIS_URL = "redis://localhost:6379"
MAX_REQUESTS = 100 # 50 requests
WINDOW = 20  # per 60 seconds

# Create Redis connection once
r = redis.from_url(REDIS_URL, encoding="utf-8", decode_responses=True)


async def rate_limiter(request: Request):
    try:
        client_ip = request.client.host
        now = int(time.time())

        # Time window bucket (changes every WINDOW seconds)
        window_start = now // WINDOW
        key = f"rate:{client_ip}:{window_start}"

        # Increment counter
        current = await r.incr(key)

        # Set expiry on first request in this window
        if current == 1:
            await r.expire(key, WINDOW)

        # Check if limit exceeded
        if current > MAX_REQUESTS:
            # Calculate how many seconds left in current window
            window_end = (window_start + 1) * WINDOW
            retry_after = window_end - now

            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail={
                    "message": "Too many requests",
                    "retry_after": retry_after  # ✅ Seconds until window resets
                },
                headers={"Retry-After": str(retry_after)}
            )

    except HTTPException:
        raise  # Let FastAPI handle it
    except Exception as e:
        print(f"⚠️ Rate limit error: {e}")
        # Don't kill app on Redis failure, just skip limiting
        pass
