package com.microservice.user.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();  // This is the email from JWT

            // Don't return 'anonymousUser' - return 'system' instead
            if (email != null && !email.equals("anonymousUser")) {
                return email;
            }
        }

        return "system";  // Fallback for unauthenticated requests
    }
}

