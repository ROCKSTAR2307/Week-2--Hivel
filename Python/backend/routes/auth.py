from fastapi import APIRouter, HTTPException, Form, status
from jose import jwt, JWTError
from datetime import datetime, timedelta
from passlib.context import CryptContext
from database import get_database
from fastapi import Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import os
from dotenv import load_dotenv

load_dotenv()

# ---------------- CONFIG ----------------
router = APIRouter(prefix="/auth", tags=["Auth"])
db = get_database()

SECRET_KEY = os.getenv("SECRET_KEY", "supersecretrockstar")  # 🔒 Falls back to default if .env missing
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24  # 1 day token lifetime

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# ---------------- HELPERS ----------------
security = HTTPBearer()


async def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)) -> dict:
    """
    Validate JWT token and return the current user's data.
    """
    try:
        token = credentials.credentials
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")

        if email is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid authentication credentials"
            )

        return {"email": email}

    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials"
        )


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify plain text password against hashed value."""
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    """Return a hashed version of the password."""
    return pwd_context.hash(password)


def create_access_token(data: dict, expires_delta: timedelta | None = None) -> str:
    """Generate a signed JWT token with expiry."""
    to_encode = data.copy()
    expire = datetime.utcnow() + (expires_delta or timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


# ---------------- ROUTES ----------------

@router.post("/register")
async def register_user(
        email: str = Form(...),
        password: str = Form(...),
):
    """
    Register a new user (email + password).
    Stores the hashed password in MongoDB.
    """
    # ✅ TRIM WHITESPACE FIRST
    email = email.strip()
    password = password.strip()

    # ✅ ADD VALIDATION
    if not email or not password:
        raise HTTPException(status_code=400, detail="Email and password cannot be empty")

    if len(password) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters")

    # Basic email validation
    if "@" not in email or "." not in email:
        raise HTTPException(status_code=400, detail="Invalid email format")

    # Check if user already exists
    existing = await db.auth.find_one({"email": email})
    if existing:
        raise HTTPException(status_code=400, detail="Email already registered")

    # Hash password
    hashed_pw = get_password_hash(password)

    # Insert into database with trimmed email
    result = await db.auth.insert_one({
        "email": email,  # Already trimmed
        "password": hashed_pw,
        "createdAt": datetime.utcnow()
    })

    print(f"✅ User registered: '{email}'")

    return {
        "id": str(result.inserted_id),
        "email": email,
        "message": "User registered successfully"
    }


@router.post("/login")
async def login_user(
    email: str = Form(...),
    password: str = Form(...),
):
    """
    Login endpoint - validates credentials and returns JWT token.
    """
    # ✂️ Trim email and password just in case
    email = email.strip()
    password = password.strip()

    print(f"DEBUG LOGIN: '{email}' '{password}'")

    # Find user by email
    user = await db.auth.find_one({"email": email})
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    # Verify password
    if not verify_password(password, user["password"]):
        raise HTTPException(status_code=401, detail="Invalid credentials")

    # Create access token
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user["email"].strip()},
        expires_delta=access_token_expires
    )

    return {
        "access_token": access_token,
        "token_type": "bearer",
        "expires_in": ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        "email": user["email"].strip(),
    }


@router.get("/me")
async def get_me(current_user: dict = Depends(get_current_user)):
    """
    Get current logged-in user's information from JWT token.
    """
    return current_user
