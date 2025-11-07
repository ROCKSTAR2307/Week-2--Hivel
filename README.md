# User Management Service 🌟

This repository contains the code for a comprehensive User Management Service, designed to handle user authentication, authorization, and data management. It provides RESTful APIs for performing CRUD operations on user data, managing authentication, and supporting features like CSV import/export and image uploads. This service is built with scalability, security, and ease of integration in mind.

## 🚀 Key Features

- **User Authentication & Authorization:** Securely manage user accounts with registration and login functionalities, protected by JWT (JSON Web Tokens).
- **Comprehensive User Management:** Perform CRUD (Create, Read, Update, Delete) operations on user data with filtering, sorting, and pagination support.
- **CSV Import/Export:** Easily import and export user data in CSV format for bulk operations.
- **Image Uploads:** Support uploading and associating images with user profiles.
- **Rate Limiting:** Protect the API from abuse with rate limiting middleware.
- **Health Checks & Metrics:** Monitor the service's health and performance with dedicated endpoints.
- **API Documentation:** Swagger UI integration for easy API exploration and testing.
- **CORS Support:** Enables cross-origin requests, allowing seamless integration with frontend applications.
- **Soft Delete:** Implements a soft delete mechanism for user records.

## 🛠️ Tech Stack

Here's a breakdown of the technologies used in this project:

| Category      | Technology                       | Description                                                                                                |
|---------------|-----------------------------------|------------------------------------------------------------------------------------------------------------|
| **Backend**   | Node.js                         | Server-side runtime environment                                                                              |
|               | Spring Boot                     | Java-based framework for building microservices                                                              |
|               | Python (FastAPI)                | Modern, fast (high-performance), web framework for building APIs                                            |
| **Database**  | MySQL                           | Relational database management system (Node.js)                                                              |
|               | PostgreSQL                      | Relational database management system (Spring Boot)                                                          |
| **ORM**       | Prisma                          | Next-generation ORM for Node.js                                                                              |
|               | Spring Data JPA                | Simplifies data access and persistence with Spring and JPA                                                  |
| **Authentication** | JWT (JSON Web Tokens)         | Standard for securely transmitting information between parties as a JSON object                               |
|               | bcrypt                          | Password hashing function                                                                                      |
| **Web Framework** | Express.js                      | Fast, unopinionated, minimalist web framework for Node.js                                                  |
|               | FastAPI                         | High-performance Python web framework                                                                        |
| **Middleware**| CORS                            | Enables Cross-Origin Resource Sharing                                                                        |
|               | Rate Limiter                    | Protects API endpoints from abuse                                                                            |
| **File Upload** | Multer                          | Node.js middleware for handling `multipart/form-data`                                                        |
|               | Spring Web                      | Spring module for building web applications                                                                 |
| **Caching**   | Redis                           | In-memory data structure store, used as a distributed, in-memory key–value database, cache and message broker |
|               | Spring Cache Abstraction         | Provides a consistent way to add caching to Spring applications                                            |
| **Build Tools** | Maven                           | Dependency Management and Build Automation (Spring Boot)                                                      |
| **Testing**   | (Implicit - e.g., JUnit, Jest) | Testing frameworks (details depend on the specific implementation in each sub-project)                        |
| **Documentation**| Swagger UI                      | API documentation and interactive testing                                                                    |
| **Other**     | dotenv                          | Loads environment variables from `.env` file                                                                 |
|               | csv-stringify/sync              | Converts data to CSV format (Node.js)                                                                       |
|               | csv-parse/sync                  | Parses CSV data (Node.js)                                                                                    |

## 📦 Getting Started / Setup Instructions

Follow these instructions to get the project up and running on your local machine.  Instructions are provided for both the Node.js and Spring Boot backends.

### Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js:** (v18 or higher) [https://nodejs.org/](https://nodejs.org/)
- **npm:** (Node Package Manager - usually comes with Node.js)
- **Java:** (JDK 17 or higher) [https://www.oracle.com/java/technologies/javase-jdk17-downloads.html](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
- **Maven:** [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)
- **Python:** (3.8 or higher) [https://www.python.org/downloads/](https://www.python.org/downloads/)
- **Docker:** (Optional, for database setup) [https://www.docker.com/get-started/](https://www.docker.com/get-started/)
- **MySQL:** (or Docker setup)
- **PostgreSQL:** (or Docker setup)
- **Redis:** (or Docker setup)

### NodeJS Setup

1.  **Clone the repository:**

    ```bash
    git clone <repository-url>
    cd NodeJS/user-service-node
    ```

2.  **Install dependencies:**

    ```bash
    npm install
    ```

3.  **Configure environment variables:**

    Create a `.env` file in the root directory and add the following (example) variables:

    ```
    DATABASE_URL="mysql://user:password@host:port/database"
    APP_BASE_URL="http://localhost:3000"
    ACCESS_TOKEN_EXPIRE_MINUTES=30
    ```

    Replace the values with your actual database credentials and application URL.

4.  **Run database migrations:**

    ```bash
    npx prisma migrate dev --name init
    npx prisma generate
    ```

### Spring Boot Setup

1.  **Clone the repository:**

    ```bash
    git clone <repository-url>
    cd spring\ backend/user-microservice-springboot
    ```

2.  **Configure application properties:**

    Modify the `src/main/resources/application.properties` file with your database and Redis configurations:

    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/your_database
    spring.datasource.username=your_username
    spring.datasource.password=your_password

    spring.redis.host=localhost
    spring.redis.port=6379
    ```

    Replace the values with your actual database and Redis credentials.

3.  **Build the application:**

    ```bash
    mvn clean install
    ```

### Python (FastAPI) Setup

1.  **Clone the repository:**

    ```bash
    git clone <repository-url>
    cd Python/backend
    ```

2.  **Create a virtual environment:**

    ```bash
    python -m venv venv
    source venv/bin/activate  # On Linux/macOS
    venv\Scripts\activate  # On Windows
    ```

3.  **Install dependencies:**

    ```bash
    pip install -r requirements.txt
    ```

4.  **Configure environment variables:**

    Create a `.env` file in the root directory and add the necessary variables (e.g., database connection details).

### Running Locally

#### NodeJS

```bash
npm run dev
```

This will start the server, typically on port 3000.  You can access the API documentation at `http://localhost:3000/swagger`.

#### Spring Boot

```bash
mvn spring-boot:run
```

This will start the Spring Boot application, typically on port 8080.

#### Python (FastAPI)

```bash
uvicorn main:app --reload
```

This will start the FastAPI application with hot reloading, typically on port 8000. You can access the API documentation at `http://localhost:8000/docs`.

## 📂 Project Structure

```
├── NodeJS/user-service-node/
│   ├── controllers/
│   │   ├── authController.js
│   │   └── userController.js
│   ├── middleware/
│   │   ├── auth.js
│   │   ├── rateLimiter.js
│   ├── routes/
│   │   ├── auth.js
│   │   └── users.js
│   ├── utils/
│   │   └── logger.js
│   ├── prisma/
│   │   ├── schema.prisma
│   │   └── migrations/
│   ├── swagger-output.json
│   ├── index.js
│   ├── package.json
│   └── .env
├── spring backend/user-microservice-springboot/
│   ├── src/main/java/com/microservice/user/
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   └── JwtAuthenticationFilter.java
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   └── UserController.java
│   │   ├── entity/
│   │   │   ├── Auth.java
│   │   │   └── User.java
│   │   ├── repository/
│   │   │   ├── AuthRepository.java
│   │   │   └── UserRepository.java
│   │   ├── service/
│   │   │   ├── FileStorageService.java
│   │   │   └── UserService.java
│   │   ├── util/
│   │   │   ├── JwtUtil.java
│   │   │   └── CurrentUser.java
│   │   ├── UserMicroserviceSpringbootApplication.java
│   ├── src/main/resources/
│   │   ├── application.properties
│   ├── pom.xml
├── Python/backend/
│   ├── main.py
│   ├── routes/
│   │   ├── users.py
│   │   └── auth.py
│   ├── rate_limit.py
│   ├── models.py
│   ├── database.py
│   ├── schemas.py
│   ├── utils.py
│   ├── .env
│   ├── requirements.txt
```



## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1.  Fork the repository.
2.  Create a new branch for your feature or bug fix.
3.  Make your changes and commit them with descriptive messages.
4.  Push your changes to your fork.
5.  Submit a pull request to the main repository.


## 💖 Thanks Message

Thank you for checking out this User Management Service! We hope it helps you build amazing applications.

This is written by [readme.ai](https://readme-generator-phi.vercel.app/).
