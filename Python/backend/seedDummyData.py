import asyncio
import httpx
from motor.motor_asyncio import AsyncIOMotorClient

MONGO_URI = "mongodb://localhost:27017"
DB_NAME = "user_directory"

async def seed_dummy_users():
    client = AsyncIOMotorClient(MONGO_URI)
    db = client[DB_NAME]

    print("🌍 Fetching dummy users from API...")
    url = "https://dummyjson.com/users?limit=200"
    async with httpx.AsyncClient() as client_http:
        resp = await client_http.get(url)
        data = resp.json()

    users = data.get("users", [])
    print(f"✅ Fetched {len(users)} users from DummyJSON")

    # Transform data to match your DB schema
    formatted_users = []
    for u in users:
        formatted_users.append({
            "firstName": u.get("firstName"),
            "lastName": u.get("lastName"),
            "email": u.get("email"),
            "phone": str(u.get("phone", "")),
            "gender": u.get("gender"),
            "city": u.get("address", {}).get("city"),
            "department": u.get("company", {}).get("department"),
            "image": u.get("image")
        })

    # Optional: clear old users before inserting
    await db.users.delete_many({})
    print("🗑 Cleared old users collection")

    # Insert new users
    result = await db.users.insert_many(formatted_users)
    print(f"✅ Inserted {len(result.inserted_ids)} users into MongoDB")

    client.close()

if __name__ == "__main__":
    asyncio.run(seed_dummy_users())
