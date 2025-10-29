# backend/models.py
from pydantic import BaseModel, EmailStr, Field
from typing import Optional

class User(BaseModel):
    id: Optional[str] = Field(None, alias="_id")
    firstName: str
    lastName: Optional[str] = None
    email: EmailStr
    phone: str
    gender: str
    city: str
    department: str
    image: Optional[str] = None

    class Config:
        json_schema_extra = {
            "example": {
                "firstName": "John",
                "lastName": "Doe",
                "email": "john@example.com",
                "phone": "+918888888888",
                "gender": "male",
                "city": "Hyderabad",
                "department": "AI",
                "image": "https://randomuser.me/api/portraits/men/1.jpg"
            }
        }
