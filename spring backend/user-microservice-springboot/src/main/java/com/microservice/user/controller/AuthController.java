package com.microservice.user.controller;

import com.microservice.user.entity.Auth;
import com.microservice.user.repository.AuthRepository;
import com.microservice.user.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
//@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ✅ Login - Accepts form-urlencoded
    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> login(@RequestParam("email") String email,
                                                     @RequestParam("password") String password) {
        try {
            if (email == null || password == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("detail", "Email and password are required"));
            }

            // Find user
            Auth auth = authRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));

            // Verify password
            if (!passwordEncoder.matches(password, auth.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("detail", "Invalid credentials"));
            }

            // Generate JWT token
            String token = jwtUtil.generateToken(email);

            Map<String, String> response = new HashMap<>();
            response.put("access_token", token);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("detail", "Login failed: " + e.getMessage()));
        }
    }

    // ✅ Register - Accepts form-urlencoded
    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> register(@RequestParam("email") String email,
                                                        @RequestParam("password") String password) {
        try {
            if (email == null || password == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("detail", "Email and password are required"));
            }

            // Check if user exists
            if (authRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("detail", "Email already registered"));
            }

            // Create new auth record
            Auth auth = new Auth();
            auth.setEmail(email);
            auth.setPassword(passwordEncoder.encode(password));
            auth.setApiKey(UUID.randomUUID().toString());
            authRepository.save(auth);

            // Generate JWT token
            String token = jwtUtil.generateToken(email);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("access_token", token));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Registration failed: " + e.getMessage()));
        }
    }
}
