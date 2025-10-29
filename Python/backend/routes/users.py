# routes/users.py
from fastapi import APIRouter, Form, UploadFile, File, HTTPException, Query, Depends, status
from typing import Optional, List, Any, Dict
from bson import ObjectId
from pydantic import BaseModel
from models import User
from database import get_database
from jose import jwt, JWTError
from fastapi.security import OAuth2PasswordBearer
import os
import io
from dotenv import load_dotenv
from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
import csv
from datetime import datetime
import pytz
from pydantic import BaseModel


load_dotenv()
# ---------------- JWT Auth Config ----------------
SECRET_KEY = os.getenv("SECRET_KEY")    # keep in env in prod
ALGORITHM = "HS256"
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


async def verify_token(token: str = Depends(oauth2_scheme)) -> str:
    """
    Validates JWT and returns the subject (email).
    Raises 401 if invalid/expired.
    """
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email = payload.get("sub")
        if not email:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token payload")
        return email
    except JWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired or invalid")


# ---------------- Setup ----------------
router = APIRouter()
db = get_database()
UPLOAD_DIR = "images"
os.makedirs(UPLOAD_DIR, exist_ok=True)
BASE_URL = "http://127.0.0.1:8000"

# ---------------- Utilities ----------------
def _truthy(s: Optional[str]) -> bool:
    return s is not None and str(s).strip() != ""


def _normalize_user_doc(raw: dict) -> dict:
    """
    Convert ObjectId -> string and make image an absolute URL if needed.
    """
    if not raw:
        return raw
    raw = dict(raw)  # copy (avoid mutating provider object)
    raw["_id"] = str(raw["_id"])
    img = raw.get("image")
    if img and not img.startswith("http"):
        if not img.startswith("/"):
            img = "/" + img
        raw["image"] = f"{BASE_URL}{img}"
    return raw


# ---------------- Response Models ----------------
class PaginatedUsers(BaseModel):
    total: int
    users: List[User]


# Allowed sort fields (whitelist to avoid injection / errors)
ALLOWED_SORT_FIELDS = {"firstName", "lastName", "email", "department", "city", "createdAt", "phone", "gender"}


# ---------------- Routes ----------------

@router.get("/departments", response_model=List[str])
async def get_departments(
    include_deleted: bool = Query(False, description="Include departments from deleted users"),
    user_email: str = Depends(verify_token)
):
    """
    Return distinct department names (non-empty), optionally including deleted records.
    """
    filter_q = {}
    if not include_deleted:
        filter_q["isDeleted"] = {"$ne": True}
    depts = await db.users.distinct("department", filter_q)
    cleaned =sorted([d for d in depts if d and str(d).strip() != ""])  # ✅ Sort here
    return cleaned


@router.get("/", response_model=PaginatedUsers)
async def get_users(
    skip: int = Query(0, ge=0),
    limit: int = Query(10, ge=1, le=100),
    search: Optional[str] = Query(None),
    gender: Optional[str] = Query(None),
    department: Optional[str] = Query(None),
    sort_by: Optional[str] = Query("firstName"),
    sort_order: Optional[str] = Query("asc"),
    user_email: str = Depends(verify_token),
):
    """
    Get active (non-deleted) users with pagination, search, filters and sorting.
    - `search` matches firstName, lastName, email (case-insensitive)
    - `gender` and `department` are exact (case-insensitive) filters (pass "all" to ignore)
    - `sort_by` must be one of ALLOWED_SORT_FIELDS
    """
    query: Dict[str, Any] = {}
    # hide soft-deleted by default
    query["isDeleted"] = {"$ne": True}

    if _truthy(search):
        s = search.strip()
        query["$or"] = [
            {"firstName": {"$regex": s, "$options": "i"}},
            {"lastName": {"$regex": s, "$options": "i"}},
            {"email": {"$regex": s, "$options": "i"}},
        ]

    if _truthy(gender) and gender.strip().lower() != "all":
        query["gender"] = {"$regex": f"^{gender.strip()}$", "$options": "i"}

    if _truthy(department) and department.strip().lower() != "all":
        query["department"] = {"$regex": f"^{department.strip()}$", "$options": "i"}

    # sanitize sort_by
    sort_field = sort_by if sort_by in ALLOWED_SORT_FIELDS else "firstName"
    sort_direction = 1 if (sort_order or "asc").lower() == "asc" else -1

    total = await db.users.count_documents(query)
    cursor = db.users.find(query).sort(sort_field, sort_direction).skip(skip).limit(limit)
    docs = await cursor.to_list(length=limit)
    users = [_normalize_user_doc(u) for u in docs]
    return {"total": total, "users": users}


@router.get("/deleted", response_model=PaginatedUsers)
async def get_deleted_users(
    skip: int = Query(0, ge=0),
    limit: int = Query(10, ge=1, le=100),
    search: Optional[str] = Query(None),
    sort_by: Optional[str] = Query("firstName"),
    sort_order: Optional[str] = Query("asc"),
    user_email: str = Depends(verify_token),
):
    """
    List soft-deleted users for management (restore / permanent delete).
    """
    query: Dict[str, Any] = {"isDeleted": True}

    if _truthy(search):
        s = search.strip()
        query["$or"] = [
            {"firstName": {"$regex": s, "$options": "i"}},
            {"lastName": {"$regex": s, "$options": "i"}},
            {"email": {"$regex": s, "$options": "i"}},
        ]

    sort_field = sort_by if sort_by in ALLOWED_SORT_FIELDS else "firstName"
    sort_direction = 1 if (sort_order or "asc").lower() == "asc" else -1

    total = await db.users.count_documents(query)
    cursor = db.users.find(query).sort(sort_field, sort_direction).skip(skip).limit(limit)
    docs = await cursor.to_list(length=limit)
    users = [_normalize_user_doc(u) for u in docs]
    return {"total": total, "users": users}


@router.get("/export")
async def export_users(
    search: Optional[str] = Query(None),        # ✅ Add search parameter
    gender: Optional[str] = Query(None),        # ✅ Add gender parameter
    department: Optional[str] = Query(None),    # ✅ Add department parameter
    user_email: str = Depends(verify_token)
):
    """
    Export all active users to CSV with all 4 audit fields.
    Declared before /{user_id} to avoid the dynamic route intercepting /export and returning 400.
    """
    try:
        query = {"isDeleted": {"$ne": True}}
        cursor = db.users.find(query)
        users = await cursor.to_list(length=None)

        output = io.StringIO()
        writer = csv.DictWriter(
            output,
            fieldnames=[
                "firstName",
                "lastName",
                "email",
                "phone",
                "gender",
                "city",
                "department",
                "image",
                'createdAt',  # ✅ Audit field 1
                'createdBy',  # ✅ Audit field 2
                'updatedAt',  # ✅ Audit field 3
                'updatedBy'  # ✅ Audit field 4
            ],
        )

        writer.writeheader()

        # ✅ Import pytz for timezone conversion

        ist_tz = pytz.timezone('Asia/Kolkata')

        for user in users:
            # ✅ Format datetime fields to IST
            created_at = user.get("createdAt")
            updated_at = user.get("updatedAt")

            # Convert to IST if datetime exists
            if created_at:
                if isinstance(created_at, datetime):
                    created_at = created_at.astimezone(ist_tz).strftime('%d/%m/%Y, %I:%M:%S %p')
                else:
                    created_at = str(created_at)
            else:
                created_at = ""

            if updated_at:
                if isinstance(updated_at, datetime):
                    updated_at = updated_at.astimezone(ist_tz).strftime('%d/%m/%Y, %I:%M:%S %p')
                else:
                    updated_at = str(updated_at)
            else:
                updated_at = ""

            writer.writerow(
                {
                    "firstName": user.get("firstName", ""),
                    "lastName": user.get("lastName", ""),
                    "email": user.get("email", ""),
                    "phone": user.get("phone", ""),
                    "gender": user.get("gender", ""),
                    "city": user.get("city", ""),
                    "department": user.get("department", ""),
                    "image": user.get("image", ""),
                    "createdAt": created_at,  # ✅ Formatted IST datetime
                    "createdBy": user.get("createdBy", "system"),  # ✅ Email who created
                    "updatedAt": updated_at,  # ✅ Formatted IST datetime
                    "updatedBy": user.get("updatedBy", "system"),  # ✅ Email who updated
                }
            )

        output.seek(0)
        return StreamingResponse(
            iter([output.getvalue()]),
            media_type="text/csv",
            headers={"Content-Disposition": "attachment; filename=users_export.csv"},
        )
    except Exception as exc:
        print(f"Export error: {exc}")
        raise HTTPException(status_code=500, detail=str(exc))


@router.get("/{user_id}", response_model=User)
async def get_user(user_id: str, user_email: str = Depends(verify_token)):
    """
    Return a single user (active or deleted).
    """
    try:
        oid = ObjectId(user_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    doc = await db.users.find_one({"_id": oid})
    if not doc:
        raise HTTPException(status_code=404, detail="User not found")
    return _normalize_user_doc(doc)


@router.post("/", response_model=User)
async def create_user(
        firstName: str = Form(..., min_length=1),  # ✅ Must have at least 1 char
        lastName: Optional[str] = Form(None),
        email: str = Form(..., min_length=1),  # ✅ Must have at least 1 char
        phone: str = Form(..., min_length=1),  # ✅ Must have at least 1 char
        gender: str = Form(..., min_length=1),  # ✅ Must have at least 1 char
        city: str = Form(..., min_length=1),  # ✅ FIXED - Cannot be empty
        department: str = Form(..., min_length=1),  # ✅ FIXED - Cannot be empty
        image: Optional[UploadFile] = File(None),
        user_email: str = Depends(verify_token),
):
    """
    Create user. Protected. All required fields must be non-empty.
    """
    # Additional check for "N/A" string
    if city.strip().upper() == "N/A":
        raise HTTPException(status_code=400, detail="City cannot be 'N/A'")

    if department.strip().upper() == "N/A":
        raise HTTPException(status_code=400, detail="Department cannot be 'N/A'")

    # duplicate email check
    existing = await db.users.find_one({"email": email})
    if existing:
        raise HTTPException(status_code=400, detail="Email already exists")

    now = datetime.utcnow()
    payload = {
        "firstName": firstName,
        "lastName": lastName,
        "email": email,
        "phone": phone,
        "gender": gender.lower() if gender else None,
        "city": city.strip(),
        "department": department.strip(),
        "image": None,
        "isDeleted": False,
        "createdAt": now,
        "updatedAt": now,
        "createdBy": user_email,
        "updatedBy": user_email,
    }

    if image and image.filename:
        safe_name = image.filename.replace(" ", "_")
        file_path = os.path.join(UPLOAD_DIR, safe_name)
        with open(file_path, "wb") as f:
            f.write(await image.read())
        payload["image"] = f"/{file_path}"

    res = await db.users.insert_one(payload)
    saved = await db.users.find_one({"_id": res.inserted_id})
    return _normalize_user_doc(saved)


@router.put("/{user_id}", response_model=User)
async def update_user(
    user_id: str,
    firstName: Optional[str] = Form(None),
    lastName: Optional[str] = Form(None),
    email: Optional[str] = Form(None),
    phone: Optional[str] = Form(None),
    gender: Optional[str] = Form(None),
    city: Optional[str] = Form(None),
    department: Optional[str] = Form(None),
    image: Optional[UploadFile] = File(None),
    user_email: str = Depends(verify_token),
):
    """
    Update user fields (partial allowed). Protected.
    """
    try:
        oid = ObjectId(user_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    if _truthy(email):
        exists = await db.users.find_one({"email": email, "_id": {"$ne": oid}})
        if exists:
            raise HTTPException(status_code=400, detail="Email already in use")

    update: Dict[str, Any] = {}
    if firstName is not None: update["firstName"] = firstName
    if lastName is not None: update["lastName"] = lastName
    if email is not None: update["email"] = email
    if phone is not None: update["phone"] = phone
    if gender is not None: update["gender"] = gender.lower() if gender else None
    if city is not None: update["city"] = city
    if department is not None: update["department"] = department

    if image and image.filename:
        safe_name = image.filename.replace(" ", "_")
        file_path = os.path.join(UPLOAD_DIR, safe_name)
        with open(file_path, "wb") as f:
            f.write(await image.read())
        update["image"] = f"/{file_path}"

    if not update:
        raise HTTPException(status_code=400, detail="No update fields provided")

    update["updatedAt"] = datetime.utcnow()
    update["updatedBy"] = user_email

    result = await db.users.update_one({"_id": oid}, {"$set": update})
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="User not found")

    doc = await db.users.find_one({"_id": oid})
    return _normalize_user_doc(doc)


@router.delete("/{user_id}")
async def soft_delete_user(user_id: str, user_email: str = Depends(verify_token)):
    """
    Soft-delete: mark isDeleted = True (recoverable).
    """
    try:
        oid = ObjectId(user_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    result = await db.users.update_one({"_id": oid}, {"$set": {"isDeleted": True, "updatedAt": datetime.utcnow()}})
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="User not found")

    return {"message": "User soft-deleted"}


@router.put("/{user_id}/restore")
async def restore_user(user_id: str, user_email: str = Depends(verify_token)):
    """
    Restore a previously soft-deleted user.
    """
    try:
        oid = ObjectId(user_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    result = await db.users.update_one({"_id": oid}, {"$set": {"isDeleted": False, "updatedAt": datetime.utcnow()}})
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="User not found")

    return {"message": "User restored"}


@router.delete("/{user_id}/permanent")
async def permanent_delete_user(user_id: str, user_email: str = Depends(verify_token)):
    """
    Permanent delete (hard delete) — irreversible.
    """
    try:
        oid = ObjectId(user_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    result = await db.users.delete_one({"_id": oid})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="User not found")

    return {"message": "User permanently deleted"}
# ✅ FIXED: Bulk Delete (Soft Delete Multiple)


# Add this model at the top with other models
class BulkUserIds(BaseModel):
    ids: List[str]


@router.post("/bulk-delete")
async def bulk_delete_users(
    body: BulkUserIds,  # ✅ Changed from ids: List[str]
    user_email: str = Depends(verify_token),
):
    """
    Soft-delete multiple users at once.
    Body: {"ids": ["userid1", "userid2", "userid3"]}
    """
    if not body.ids:
        raise HTTPException(status_code=400, detail="No user IDs provided")

    try:
        oids = [ObjectId(uid) for uid in body.ids]
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    result = await db.users.update_many(
        {"_id": {"$in": oids}},
        {"$set": {"isDeleted": True, "updatedAt": datetime.utcnow()}}
    )

    return {
        "message": f"{result.modified_count} users deleted successfully",
        "deleted_count": result.modified_count
    }


# ✅ FIXED: Bulk Restore
@router.post("/bulk-restore")
async def bulk_restore_users(
    body: BulkUserIds,  # ✅ Changed
    user_email: str = Depends(verify_token),
):
    """
    Restore multiple soft-deleted users.
    Body: {"ids": ["userid1", "userid2", "userid3"]}
    """
    if not body.ids:
        raise HTTPException(status_code=400, detail="No user IDs provided")

    try:
        oids = [ObjectId(uid) for uid in body.ids]
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    result = await db.users.update_many(
        {"_id": {"$in": oids}},
        {"$set": {"isDeleted": False, "updatedAt": datetime.utcnow()}}
    )

    return {
        "message": f"{result.modified_count} users restored successfully",
        "restored_count": result.modified_count
    }


# ✅ FIXED: Bulk Permanent Delete
@router.post("/bulk-permanent-delete")
async def bulk_permanent_delete_users(
    body: BulkUserIds,  # ✅ Changed
    user_email: str = Depends(verify_token),
):
    """
    PERMANENTLY delete multiple users (irreversible).
    Body: {"ids": ["userid1", "userid2", "userid3"]}
    """
    if not body.ids:
        raise HTTPException(status_code=400, detail="No user IDs provided")

    try:
        oids = [ObjectId(uid) for uid in body.ids]
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    result = await db.users.delete_many({"_id": {"$in": oids}})

    return {
        "message": f"{result.deleted_count} users permanently deleted",
        "deleted_count": result.deleted_count
    }


@router.post("/import")
async def import_users(
        file: UploadFile = File(...),
        user_email: str = Depends(verify_token)
):
    """
    Bulk import users from CSV file.
    Required columns: firstName, email, phone, gender
    Optional columns: lastName, city, department, image

    Returns: Summary with imported count, skipped count, and detailed errors
    """
    if not file.filename.endswith('.csv'):
        raise HTTPException(status_code=400, detail="Only CSV files are allowed")

    try:
        contents = await file.read()
        decoded = contents.decode('utf-8').splitlines()
        reader = csv.DictReader(decoded)

        imported_count = 0
        skipped_count = 0
        errors = []
        row_number = 1  # Track row for better error reporting

        for row in reader:
            row_number += 1
            try:
                # ✅ VALIDATION 1: Check required fields
                missing_fields = []
                if not row.get('firstName', '').strip():
                    missing_fields.append('firstName')
                if not row.get('email', '').strip():
                    missing_fields.append('email')
                if not row.get('phone', '').strip():
                    missing_fields.append('phone')
                if not row.get('gender', '').strip():
                    missing_fields.append('gender')

                if missing_fields:
                    skipped_count += 1
                    errors.append({
                        "row": row_number,
                        "email": row.get('email', 'N/A'),
                        "reason": f"Missing required fields: {', '.join(missing_fields)}"
                    })
                    continue

                # ✅ VALIDATION 2: Email format
                email = row['email'].strip()
                if '@' not in email or '.' not in email:
                    skipped_count += 1
                    errors.append({
                        "row": row_number,
                        "email": email,
                        "reason": "Invalid email format"
                    })
                    continue

                # ✅ VALIDATION 3: Check duplicate email
                existing = await db.users.find_one({"email": email})
                if existing:
                    skipped_count += 1
                    errors.append({
                        "row": row_number,
                        "email": email,
                        "reason": "Email already exists in database"
                    })
                    continue

                # ✅ VALIDATION 4: Gender must be male/female
                gender = row.get('gender', '').strip().lower()
                if gender not in ['male', 'female']:
                    skipped_count += 1
                    errors.append({
                        "row": row_number,
                        "email": email,
                        "reason": f"Invalid gender '{row.get('gender')}' (must be 'male' or 'female')"
                    })
                    continue

                # ✅ VALIDATION 5: Phone format (basic check)
                phone = row.get('phone', '').strip()
                if len(phone) < 10:
                    skipped_count += 1
                    errors.append({
                        "row": row_number,
                        "email": email,
                        "reason": "Phone number too short (minimum 10 digits)"
                    })
                    continue

                # ✅ ALL VALIDATIONS PASSED - Insert user
                now = datetime.utcnow()
                user_data = {
                    "firstName": row.get('firstName', '').strip(),
                    "lastName": row.get('lastName', '').strip(),
                    "email": email,
                    "phone": phone,
                    "gender": gender,
                    "city": row.get('city', '').strip(),
                    "department": row.get('department', '').strip(),
                    "image": row.get('image', '').strip() or None,
                    "isDeleted": False,
                    "createdBy": user_email,  # ✅ ADD THIS LINE
                    "updatedBy": user_email,  # ✅ ADD THIS LINE
                    "createdAt": now,
                    "updatedAt": now
                }

                await db.users.insert_one(user_data)
                imported_count += 1

            except Exception as e:
                skipped_count += 1
                errors.append({
                    "row": row_number,
                    "email": row.get('email', 'N/A'),
                    "reason": f"Unexpected error: {str(e)}"
                })

        # Return detailed summary
        return {
            "success": True,
            "message": "Import completed",
            "imported": imported_count,
            "skipped": skipped_count,
            "total_rows": row_number - 1,
            "errors": errors  # Full error list
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Import failed: {str(e)}")


@router.post("/import/preview")
async def preview_import(
        file: UploadFile = File(...),
        user_email: str = Depends(verify_token)
):
    """
    Preview CSV import - validate all rows and return summary without inserting.
    """
    if not file.filename.endswith('.csv'):
        raise HTTPException(status_code=400, detail="Only CSV files are allowed")

    try:
        contents = await file.read()
        decoded = contents.decode('utf-8').splitlines()
        reader = csv.DictReader(decoded)

        valid_users = []
        errors = []
        row_number = 1

        for row in reader:
            row_number += 1

            # Validate each row
            missing_fields = []
            if not row.get('firstName', '').strip():
                missing_fields.append('firstName')
            if not row.get('email', '').strip():
                missing_fields.append('email')
            if not row.get('phone', '').strip():
                missing_fields.append('phone')
            if not row.get('gender', '').strip():
                missing_fields.append('gender')

            if missing_fields:
                errors.append({
                    "row": row_number,
                    "email": row.get('email', 'N/A'),
                    "reason": f"Missing required fields: {', '.join(missing_fields)}"
                })
                continue

            email = row['email'].strip()

            # Email format check
            if '@' not in email or '.' not in email:
                errors.append({
                    "row": row_number,
                    "email": email,
                    "reason": "Invalid email format"
                })
                continue

            # Check duplicate in database
            existing = await db.users.find_one({"email": email})
            if existing:
                errors.append({
                    "row": row_number,
                    "email": email,
                    "reason": "Email already exists in database"
                })
                continue

            # Gender validation
            gender = row.get('gender', '').strip().lower()
            if gender not in ['male', 'female']:
                errors.append({
                    "row": row_number,
                    "email": email,
                    "reason": f"Invalid gender '{row.get('gender')}' (must be 'male' or 'female')"
                })
                continue

            # Phone validation
            phone = row.get('phone', '').strip()
            if len(phone) < 10:
                errors.append({
                    "row": row_number,
                    "email": email,
                    "reason": "Phone number too short (minimum 10 digits)"
                })
                continue

            # All validations passed
            valid_users.append(row)

        return {
            "total_rows": row_number - 1,
            "valid_users": len(valid_users),
            "invalid_users": len(errors),
            "errors": errors
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Preview failed: {str(e)}")


@router.post("/import/confirm")
async def confirm_import(
        file: UploadFile = File(...),
        user_email: str = Depends(verify_token)
):
    """
    Actually import the valid users (skip invalid ones).
    """
    if not file.filename.endswith('.csv'):
        raise HTTPException(status_code=400, detail="Only CSV files are allowed")

    try:
        contents = await file.read()
        decoded = contents.decode('utf-8').splitlines()
        reader = csv.DictReader(decoded)

        imported_count = 0
        skipped_count = 0
        errors = []
        row_number = 1

        for row in reader:
            row_number += 1
            try:
                # Validate required fields
                missing_fields = []
                if not row.get('firstName', '').strip():
                    missing_fields.append('firstName')
                if not row.get('email', '').strip():
                    missing_fields.append('email')
                if not row.get('phone', '').strip():
                    missing_fields.append('phone')
                if not row.get('gender', '').strip():
                    missing_fields.append('gender')

                if missing_fields:
                    skipped_count += 1
                    continue

                email = row['email'].strip()

                # Email format check
                if '@' not in email or '.' not in email:
                    skipped_count += 1
                    continue

                # Check duplicate
                existing = await db.users.find_one({"email": email})
                if existing:
                    skipped_count += 1
                    continue

                # Gender validation
                gender = row.get('gender', '').strip().lower()
                if gender not in ['male', 'female']:
                    skipped_count += 1
                    continue

                # Phone validation
                phone = row.get('phone', '').strip()
                if len(phone) < 10:
                    skipped_count += 1
                    continue

                # All validations passed - Insert user
                now = datetime.utcnow()
                user_data = {
                    "firstName": row.get('firstName', '').strip(),
                    "lastName": row.get('lastName', '').strip(),
                    "email": email,
                    "phone": phone,
                    "gender": gender,
                    "city": row.get('city', '').strip(),
                    "department": row.get('department', '').strip(),
                    "image": row.get('image', '').strip() or None,
                    "isDeleted": False,
                    "createdAt": now,
                    "updatedAt": now,
                    "createdBy": user_email,  # ✅ ADD THIS LINE
                    "updatedBy": user_email  # ✅ ADD THIS LINE
                }

                await db.users.insert_one(user_data)
                imported_count += 1

            except Exception as e:
                skipped_count += 1
                errors.append(str(e))

        return {
            "success": True,
            "message": "Import completed",
            "imported": imported_count,
            "skipped": skipped_count,
            "total_rows": row_number - 1
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Import failed: {str(e)}")


# ---------------- DEBUG: Show Audit Fields ----------------
# ---------------- DEBUG: Show Audit Fields ----------------
@router.get("/debug/{user_id}")
async def debug_user(user_id: str):
    """
    🔍 Debug endpoint - Shows ALL fields including audit metadata
    """
    try:
        db = get_database()  # ✅ No await needed

        # Validate ObjectId
        if not ObjectId.is_valid(user_id):
            raise HTTPException(status_code=400, detail="Invalid user ID format")

        user = await db.users.find_one({"_id": ObjectId(user_id)})

        if not user:
            raise HTTPException(status_code=404, detail="User not found")

        # Convert ObjectId to string
        user["_id"] = str(user["_id"])

        # Return ALL fields (including audit fields)
        return {
            "success": True,
            "message": "User debug info",
            "data": {
                "id": user["_id"],
                "firstName": user.get("firstName"),
                "lastName": user.get("lastName"),
                "email": user.get("email"),
                "phone": user.get("phone"),
                "gender": user.get("gender"),
                "city": user.get("city"),
                "department": user.get("department"),
                "image": user.get("image"),
                "isDeleted": user.get("isDeleted"),
                # ✅ AUDIT FIELDS
                "createdAt": user.get("createdAt").isoformat() if user.get("createdAt") else None,
                "updatedAt": user.get("updatedAt").isoformat() if user.get("updatedAt") else None,
                "createdBy": user.get("createdBy", "system"),
                "updatedBy": user.get("updatedBy", "system")
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

