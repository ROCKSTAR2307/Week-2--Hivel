package com.microservice.user.controller;

import com.microservice.user.entity.User;
import com.microservice.user.service.UserService;
import com.microservice.user.service.FileStorageService;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.tags.Tag;


import java.io.InputStreamReader;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FileStorageService fileStorageService;

    // Helper method to convert user to DTO
    private Map<String, Object> userToDto(User user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", user.getId());
        dto.put("_id", user.getId().toString());
        dto.put("firstName", user.getFirstName());
        dto.put("lastName", user.getLastName());
        dto.put("email", user.getEmail());
        dto.put("phone", user.getPhone());
        dto.put("gender", user.getGender());
        dto.put("city", user.getCity());
        dto.put("department", user.getDepartment());

        String image = user.getImage();
        if (image != null && !image.isEmpty() && !image.startsWith("http")) {
            image = "http://localhost:8080" + image;
        }
        dto.put("image", image);

        dto.put("createdBy", user.getCreatedBy());
        dto.put("createdAt", user.getCreatedAt());
        dto.put("updatedBy", user.getUpdatedBy());
        dto.put("updatedAt", user.getUpdatedAt());
        dto.put("isDeleted", user.getIsDeleted());

        return dto;
    }

    // Helper method to convert IDs to Long list
    private List<Long> convertToLongList(Object idsObj) {
        List<Long> ids = new ArrayList<>();
        if (idsObj instanceof List<?>) {
            for (Object idObj : (List<?>) idsObj) {
                if (idObj instanceof Integer) {
                    ids.add(((Integer) idObj).longValue());
                } else if (idObj instanceof Long) {
                    ids.add((Long) idObj);
                } else if (idObj instanceof String) {
                    try {
                        ids.add(Long.parseLong((String) idObj));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid ID format: " + idObj);
                    }
                }
            }
        }
        return ids;
    }

    // Get all users with filters
    // Get all users with filters - FIXED to actually use department and gender filters
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "firstName") String sort_by,
            @RequestParam(defaultValue = "asc") String sort_order,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String gender) {

        Page<User> userPage;

        // If search is provided, use search
        if (search != null && !search.isEmpty()) {
            userPage = userService.searchUsers(search, skip, limit);
        }
        // If department or gender filter is provided, use filtered query
        else if ((department != null && !department.isEmpty()) || (gender != null && !gender.isEmpty())) {
            userPage = userService.getFilteredUsers(skip, limit, sort_by, sort_order, department, gender);
        }
        // Otherwise, get all users
        else {
            userPage = userService.getAllUsers(skip, limit, sort_by, sort_order);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("users", userPage.getContent().stream()
                .map(this::userToDto)
                .collect(Collectors.toList()));
        response.put("total", userPage.getTotalElements());

        return ResponseEntity.ok(response);
    }


    // Get deleted users
    @GetMapping("/deleted")
    public ResponseEntity<Map<String, Object>> getDeletedUsers(
            @RequestParam(defaultValue = "100") int limit) {
        List<User> deletedUsers = userService.getDeletedUsers();

        Map<String, Object> response = new HashMap<>();
        response.put("users", deletedUsers.stream()
                .limit(limit)
                .map(this::userToDto)
                .collect(Collectors.toList()));
        response.put("total", deletedUsers.size());

        return ResponseEntity.ok(response);
    }

    // Get departments
    @GetMapping("/departments")
    public ResponseEntity<List<String>> getDepartments() {
        List<String> departments = userService.getUniqueDepartments();
        return ResponseEntity.ok(departments);
    }

    // Get user by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(userToDto(user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    // Create user
    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        try {
            User user = new User();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setPhone(phone);
            user.setGender(gender);
            user.setCity(city);
            user.setDepartment(department);
            user.setCreatedBy("system");

            if (image != null && !image.isEmpty()) {
                String imageUrl = fileStorageService.storeFile(image);
                user.setImage(imageUrl);
            }

            User newUser = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(userToDto(newUser));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    // Update user
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long id,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String department,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        try {
            User userDetails = new User();
            if (firstName != null) userDetails.setFirstName(firstName);
            if (lastName != null) userDetails.setLastName(lastName);
            if (phone != null) userDetails.setPhone(phone);
            if (gender != null) userDetails.setGender(gender);
            if (city != null) userDetails.setCity(city);
            if (department != null) userDetails.setDepartment(department);

            if (image != null && !image.isEmpty()) {
                String imageUrl = fileStorageService.storeFile(image);
                userDetails.setImage(imageUrl);
            }

            User updatedUser = userService.updateUser(id, userDetails);
            return ResponseEntity.ok(userToDto(updatedUser));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    // Delete user (soft delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    // Permanent delete
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Map<String, Object>> permanentDelete(@PathVariable Long id) {
        try {
            userService.permanentDelete(id);
            return ResponseEntity.ok(Map.of("message", "User permanently deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    // Restore user
    @PutMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreUser(@PathVariable Long id) {
        try {
            User restoredUser = userService.restoreUser(id);
            return ResponseEntity.ok(Map.of("message", "User restored successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("detail", e.getMessage()));
        }
    }

    // Bulk delete - FIXED for Integer/Long conversion
    @PostMapping("/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, Object> request) {
        try {
            if (request == null || !request.containsKey("ids")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("detail", "No user IDs provided"));
            }

            List<Long> ids = convertToLongList(request.get("ids"));
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("detail", "No valid user IDs provided"));
            }

            userService.bulkDeleteUsers(ids);
            return ResponseEntity.ok(Map.of("message", ids.size() + " users deleted"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", e.getMessage() != null ? e.getMessage() : "Bulk delete failed"));
        }
    }

    // Bulk restore - FIXED for Integer/Long conversion
    @PostMapping("/bulk-restore")
    public ResponseEntity<Map<String, Object>> bulkRestore(@RequestBody Map<String, Object> request) {
        try {
            if (request == null || !request.containsKey("ids")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("detail", "No user IDs provided"));
            }

            List<Long> ids = convertToLongList(request.get("ids"));
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("detail", "No valid user IDs provided"));
            }

            userService.bulkRestoreUsers(ids);
            return ResponseEntity.ok(Map.of("message", ids.size() + " users restored"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", e.getMessage() != null ? e.getMessage() : "Bulk restore failed"));
        }
    }

    // Bulk permanent delete - FIXED for Integer/Long conversion
    @PostMapping("/bulk-permanent-delete")
    public ResponseEntity<Map<String, Object>> bulkPermanentDelete(@RequestBody Map<String, Object> request) {
        try {
            if (request == null || !request.containsKey("ids")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("detail", "No user IDs provided"));
            }

            List<Long> ids = convertToLongList(request.get("ids"));
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("detail", "No valid user IDs provided"));
            }

            userService.bulkPermanentDeleteUsers(ids);
            return ResponseEntity.ok(Map.of("message", ids.size() + " users permanently deleted"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", e.getMessage() != null ? e.getMessage() : "Bulk permanent delete failed"));
        }
    }

    // CSV Import Preview
    @PostMapping("/import/preview")
    public ResponseEntity<Map<String, Object>> importPreview(@RequestParam("file") MultipartFile file) {
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()));
            List<String[]> allRows = reader.readAll();
            reader.close();

            if (allRows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("detail", "CSV file is empty"));
            }

            List<String[]> dataRows = allRows.subList(1, allRows.size());

            List<Map<String, String>> preview = dataRows.stream()
                    .limit(5)
                    .map(row -> {
                        Map<String, String> userMap = new HashMap<>();
                        userMap.put("firstName", row.length > 0 ? row[0] : "");
                        userMap.put("lastName", row.length > 1 ? row[1] : "");
                        userMap.put("email", row.length > 2 ? row[2] : "");
                        userMap.put("phone", row.length > 3 ? row[3] : "");
                        userMap.put("gender", row.length > 4 ? row[4] : "");
                        userMap.put("city", row.length > 5 ? row[5] : "");
                        userMap.put("department", row.length > 6 ? row[6] : "");
                        return userMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("total_rows", dataRows.size());
            response.put("preview", preview);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Import preview failed: " + e.getMessage()));
        }
    }

    // CSV Import Confirm
    @PostMapping("/import/confirm")
    public ResponseEntity<Map<String, Object>> importConfirm(@RequestParam("file") MultipartFile file) {
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()));
            List<String[]> allRows = reader.readAll();
            reader.close();

            if (allRows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("detail", "CSV file is empty"));
            }

            List<String[]> dataRows = allRows.subList(1, allRows.size());
            int imported = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < dataRows.size(); i++) {
                String[] row = dataRows.get(i);
                try {
                    User user = new User();
                    user.setFirstName(row.length > 0 ? row[0] : "");
                    user.setLastName(row.length > 1 ? row[1] : "");
                    user.setEmail(row.length > 2 ? row[2] : "");
                    user.setPhone(row.length > 3 ? row[3] : "");
                    user.setGender(row.length > 4 ? row[4] : "");
                    user.setCity(row.length > 5 ? row[5] : "");
                    user.setDepartment(row.length > 6 ? row[6] : "");
                    user.setImage(row.length > 7 ? row[7] : "");
                    user.setCreatedBy("csv_import");

                    userService.createUser(user);
                    imported++;
                } catch (Exception e) {
                    skipped++;
                    errors.add("Row " + (i + 2) + ": " + e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("imported", imported);
            response.put("skipped", skipped);
            response.put("total_rows", dataRows.size());
            response.put("errors", errors);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Import failed: " + e.getMessage()));
        }
    }

    // Export to CSV - NOW RESPECTS FILTERS
    // Export to CSV - NOW RESPECTS FILTERS AND INCLUDES AUDIT FIELDS
    @GetMapping("/export")
    public ResponseEntity<String> exportCSV(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String gender,
            @RequestParam(defaultValue = "firstName") String sort_by,
            @RequestParam(defaultValue = "asc") String sort_order) {
        try {
            List<User> users;

            // Apply same filter logic as getAllUsers
            if (search != null && !search.isEmpty()) {
                users = userService.searchUsersForExport(search, sort_by, sort_order);
            } else if ((department != null && !department.isEmpty()) || (gender != null && !gender.isEmpty())) {
                users = userService.getFilteredUsersForExport(department, gender, sort_by, sort_order);
            } else {
                users = userService.getAllUsersForExport(sort_by, sort_order);
            }

            StringWriter writer = new StringWriter();
            CSVWriter csvWriter = new CSVWriter(writer);

            // ✅ Updated header with createdBy and updatedBy
            String[] header = {"id", "firstName", "lastName", "email", "phone", "gender", "city", "department", "image", "createdBy", "createdAt", "updatedBy", "updatedAt"};
            csvWriter.writeNext(header);

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            for (User user : users) {
                String[] data = {
                        user.getId().toString(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getPhone() != null ? user.getPhone() : "",
                        user.getGender() != null ? user.getGender() : "",
                        user.getCity() != null ? user.getCity() : "",
                        user.getDepartment() != null ? user.getDepartment() : "",
                        user.getImage() != null ? user.getImage() : "",
                        user.getCreatedBy() != null ? user.getCreatedBy() : "",  // ✅ Added createdBy
                        user.getCreatedAt() != null ? user.getCreatedAt().format(formatter) : "",
                        user.getUpdatedBy() != null ? user.getUpdatedBy() : "",  // ✅ Added updatedBy
                        user.getUpdatedAt() != null ? user.getUpdatedAt().format(formatter) : ""
                };
                csvWriter.writeNext(data);
            }

            csvWriter.close();

            String filename = "users_export";
            if (department != null && !department.isEmpty()) {
                filename += "_" + department.toLowerCase().replace(" ", "_");
            }
            if (gender != null && !gender.isEmpty()) {
                filename += "_" + gender.toLowerCase();
            }
            if (search != null && !search.isEmpty()) {
                filename += "_search";
            }
            filename += "_" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv";

            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .body(writer.toString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Export failed: " + e.getMessage());
        }
    }



    // Metrics endpoint
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> response = new HashMap<>();
        response.put("totalUsers", userService.countActiveUsers());
        response.put("deletedUsers", userService.countDeletedUsers());
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "user-microservice-java");
        return ResponseEntity.ok(response);
    }

    // Health check
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("database", "connected");
        return ResponseEntity.ok(response);
    }
}
