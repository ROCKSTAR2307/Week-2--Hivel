package com.microservice.user.repository;

import com.microservice.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Find by isDeleted status with pagination and sorting
    Page<User> findByIsDeleted(Boolean isDeleted, Pageable pageable);

    // Find by isDeleted with sorting (for export - no pagination)
    List<User> findByIsDeleted(Boolean isDeleted, Sort sort);

    // Find all deleted users
    List<User> findByIsDeletedTrue();

    // Find all active users (for simple export without sorting)
    List<User> findByIsDeletedFalse();

    // Search users by name or email (with pagination)
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    // Search users for export (no pagination, with sorting)
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +  
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> searchUsersForExport(@Param("search") String search, Sort sort);

    // Find users by filters (department and/or gender) with pagination and sorting
    @Query("SELECT u FROM User u WHERE u.isDeleted = false " +
            "AND (:department IS NULL OR :department = '' OR u.department = :department) " +
            "AND (:gender IS NULL OR :gender = '' OR u.gender = :gender)")
    Page<User> findByFilters(@Param("department") String department,
                             @Param("gender") String gender,
                             Pageable pageable);

    // Find users by filters for export (no pagination, with sorting)
    @Query("SELECT u FROM User u WHERE u.isDeleted = false " +
            "AND (:department IS NULL OR :department = '' OR u.department = :department) " +
            "AND (:gender IS NULL OR :gender = '' OR u.gender = :gender)")
    List<User> findByFiltersForExport(@Param("department") String department,
                                      @Param("gender") String gender,
                                      Sort sort);

    // Find distinct departments
    @Query("SELECT DISTINCT u.department FROM User u WHERE u.department IS NOT NULL AND u.isDeleted = false")
    List<String> findDistinctDepartments();

    // Count by isDeleted status
    long countByIsDeleted(Boolean isDeleted);

    // Find user by email
    Optional<User> findByEmail(String email);
}
