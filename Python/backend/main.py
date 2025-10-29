from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
from routes import users, auth
import os
import traceback

# ---------------- App Initialization ----------------
app = FastAPI(title="User Management System")

# ---------------- CORS - MUST BE FIRST! ----------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5174",  # ✅ ADDED
        "http://127.0.0.1:5174"  # ✅ ADDED
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["*"],
)

# ---------------- Static Files ----------------
os.makedirs("images", exist_ok=True)
app.mount("/images", StaticFiles(directory="images"), name="images")

# ---------------- Routers ----------------
app.include_router(auth.router)
app.include_router(users.router, prefix="/api/users", tags=["Users"])

# ---------------- Rate limiter import ----------------
from rate_limit import rate_limiter


# ---------------- Global rate-limit middleware ----------------
@app.middleware("http")
async def global_rate_limit(request: Request, call_next):
    skip_prefixes = ("/images", "/auth", "/docs", "/openapi.json", "/redoc")
    if request.url.path.startswith(skip_prefixes):
        return await call_next(request)

    try:
        if request.url.path.startswith("/api/"):
            await rate_limiter(request)

        response = await call_next(request)
        return response

    except HTTPException as exc:
        # ✅ FIX: Add CORS headers manually to error responses
        response = JSONResponse(
            status_code=exc.status_code,
            content=exc.detail
        )

        # ✅ UPDATED: Dynamic origin matching
        origin = request.headers.get("origin")
        allowed_origins = [
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174"
        ]

        if origin in allowed_origins:
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Access-Control-Allow-Credentials"] = "true"
            response.headers["Access-Control-Allow-Methods"] = "*"
            response.headers["Access-Control-Allow-Headers"] = "*"

        # Add Retry-After header if it's a 429
        if exc.status_code == 429 and isinstance(exc.detail, dict):
            retry_after = exc.detail.get("retry_after", 60)
            response.headers["Retry-After"] = str(retry_after)

        return response

    except Exception as e:
        traceback.print_exc()
        response = JSONResponse(
            status_code=500,
            content={"detail": "Internal Server Error"}
        )

        # ✅ UPDATED: Also add CORS to 500 errors
        origin = request.headers.get("origin")
        allowed_origins = [
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174"
        ]

        if origin in allowed_origins:
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Access-Control-Allow-Credentials"] = "true"

        return response


# ---------------- Root Endpoint ----------------
@app.get("/")
def root():
    return {"message": "🚀 FastAPI backend running successfully!"}


# ---------------- Health Endpoint ----------------
@app.get("/health")
def health():
    return {
        "status": "healthy",
        "database": "MongoDB",
        "message": "User service is running"
    }
