package com.microservice.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        try {
            // ✅ Validate file
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("Cannot store empty file");
            }

            // ✅ Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new RuntimeException("Only image files are allowed");
            }

            // ✅ Validate file size (10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new RuntimeException("File size exceeds 10MB limit");
            }

            // ✅ Sanitize filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = "upload.jpg";
            }

            // Remove any path traversal characters
            originalFilename = originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
            Path targetLocation = uploadPath.resolve(fileName);

            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path
            return "/uploads/" + fileName;

        } catch (IOException ex) {
            throw new RuntimeException("Could not store file. Please try again!", ex);
        }
    }

    // ✅ ADDED: Delete file method
    public void deleteFile(String filePath) {
        try {
            if (filePath != null && !filePath.isEmpty()) {
                // Remove "/uploads/" prefix if present
                String fileName = filePath.replace("/uploads/", "");
                Path fileToDelete = Paths.get(uploadDir).resolve(fileName);

                if (Files.exists(fileToDelete)) {
                    Files.delete(fileToDelete);
                }
            }
        } catch (IOException ex) {
            // Log but don't throw - file deletion is not critical
            System.err.println("Could not delete file " + filePath + ": " + ex.getMessage());
        }
    }

    // ✅ ADDED: Get file path method
    public Path getFilePath(String fileName) {
        return Paths.get(uploadDir).resolve(fileName);
    }
}
