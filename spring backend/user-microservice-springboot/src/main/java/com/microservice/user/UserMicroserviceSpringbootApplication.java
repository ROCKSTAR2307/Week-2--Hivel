package com.microservice.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication //Enables config, component scan and autoconfig annotations.
@EnableCaching
public class UserMicroserviceSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserMicroserviceSpringbootApplication.class, args);
        System.out.println("\n✅ User Microservice Started Successfully!");
        System.out.println("📍 Running on: http://localhost:8080");
        System.out.println("🗄️  Database: PostgreSQL (week2)");
        System.out.println("⚡ Cache: Redis");
    }
}
