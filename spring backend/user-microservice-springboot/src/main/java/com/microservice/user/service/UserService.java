package com.microservice.user.service;

import com.microservice.user.entity.User;
import com.microservice.user.repository.UserRepository;
import com.microservice.user.util.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // ============= READ OPERATIONS (CACHED) =============

    // Get all users with pagination and sorting
    //@Cacheable(value = "users", key = "'all-' + #skip + '-' + #limit + '-' + #sortBy + '-' + #sortOrder")
    public Page<User> getAllUsers(int skip, int limit, String sortBy, String sortOrder) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        int page = skip / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by(direction, sortBy));
        return userRepository.findByIsDeleted(false, pageable);
    }

    // Search users
   // @Cacheable(value = "userSearch", key = "'search-' + #search + '-' + #skip + '-' + #limit")
    public Page<User> searchUsers(String search, int skip, int limit) {
        int page = skip / limit;
        Pageable pageable = PageRequest.of(page, limit);
        return userRepository.searchUsers(search, pageable);
    }

    // Get filtered users by department and/or gender
   // @Cacheable(value = "filteredUsers", key = "'filter-' + #skip + '-' + #limit + '-' + #sortBy + '-' + #sortOrder + '-' + #department + '-' + #gender")
    public Page<User> getFilteredUsers(int skip, int limit, String sortBy, String sortOrder, String department, String gender) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        int page = skip / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by(direction, sortBy));
        return userRepository.findByFilters(department, gender, pageable);
    }

    // Get user by ID
    @Cacheable(value = "user", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    // Get unique departments
    @Cacheable(value = "departments")
    public List<String> getUniqueDepartments() {
        return userRepository.findDistinctDepartments();
    }

    // Get deleted users (no cache - small dataset, changes frequently)
    public List<User> getDeletedUsers() {
        return userRepository.findByIsDeletedTrue();
    }

    // ============= EXPORT OPERATIONS (NO CACHE - Large datasets) =============

    // Get all users for export (no pagination)
    public List<User> getAllUsersForExport() {
        return userRepository.findByIsDeletedFalse();
    }

    // Get all users for export WITH SORTING
    public List<User> getAllUsersForExport(String sortBy, String sortOrder) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        return userRepository.findByIsDeleted(false, sort);
    }

    // Get filtered users for export
    public List<User> getFilteredUsersForExport(String department, String gender, String sortBy, String sortOrder) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        return userRepository.findByFiltersForExport(department, gender, sort);
    }

    // Search users for export
    public List<User> searchUsersForExport(String search, String sortBy, String sortOrder) {
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        return userRepository.searchUsersForExport(search, sort);
    }

    // ============= WRITE OPERATIONS (CACHE EVICTION) =============

    // Create user - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "departments"}, allEntries = true)
    @Transactional
    public User createUser(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        user.setIsDeleted(false);
        String currentUserEmail = CurrentUser.getCurrentUserEmail();
        user.setCreatedBy(currentUserEmail);

        return userRepository.save(user);
    }

    // Update user - Clears all caches
    //@CacheEvict(value = {"users", "userSearch", "filteredUsers", "user", "departments"}, allEntries = true)
    @Transactional
    public User updateUser(Long id, User userDetails) {
        User user = getUserById(id);

        if (userDetails.getFirstName() != null && !userDetails.getFirstName().isEmpty()) {
            user.setFirstName(userDetails.getFirstName());
        }
        if (userDetails.getLastName() != null && !userDetails.getLastName().isEmpty()) {
            user.setLastName(userDetails.getLastName());
        }
        if (userDetails.getPhone() != null && !userDetails.getPhone().isEmpty()) {
            user.setPhone(userDetails.getPhone());
        }
        if (userDetails.getGender() != null && !userDetails.getGender().isEmpty()) {
            user.setGender(userDetails.getGender());
        }
        if (userDetails.getCity() != null && !userDetails.getCity().isEmpty()) {
            user.setCity(userDetails.getCity());
        }
        if (userDetails.getDepartment() != null && !userDetails.getDepartment().isEmpty()) {
            user.setDepartment(userDetails.getDepartment());
        }
        if (userDetails.getImage() != null && !userDetails.getImage().isEmpty()) {
            user.setImage(userDetails.getImage());
        }

        user.setUpdatedBy(CurrentUser.getCurrentUserEmail());
        return userRepository.save(user);
    }

    // Soft delete user - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "user"}, allEntries = true)
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        user.setIsDeleted(true);
        user.setUpdatedBy(CurrentUser.getCurrentUserEmail());
        userRepository.save(user);
    }

    // Permanent delete - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "user"}, allEntries = true)
    @Transactional
    public void permanentDelete(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
    }

    // Restore user - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "user"}, allEntries = true)
    @Transactional
    public User restoreUser(Long id) {
        User user = getUserById(id);
        user.setIsDeleted(false);
        user.setUpdatedBy(CurrentUser.getCurrentUserEmail());
        return userRepository.save(user);
    }

    // ============= BULK OPERATIONS (CACHE EVICTION) =============

    // Bulk delete users - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "user"}, allEntries = true)
    @Transactional
    public void bulkDeleteUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("No user IDs provided");
        }

        String currentUserEmail = CurrentUser.getCurrentUserEmail();

        for (Long id : ids) {
            try {
                User user = getUserById(id);
                user.setIsDeleted(true);
                user.setUpdatedBy(currentUserEmail);
                userRepository.save(user);
            } catch (Exception e) {
                System.err.println("Failed to delete user " + id + ": " + e.getMessage());
            }
        }
    }

    // Bulk restore users - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "user"}, allEntries = true)
    @Transactional
    public void bulkRestoreUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("No user IDs provided");
        }

        String currentUserEmail = CurrentUser.getCurrentUserEmail();

        for (Long id : ids) {
            try {
                User user = getUserById(id);
                user.setIsDeleted(false);
                user.setUpdatedBy(currentUserEmail);
                userRepository.save(user);
            } catch (Exception e) {
                System.err.println("Failed to restore user " + id + ": " + e.getMessage());
            }
        }
    }

    // Bulk permanent delete users - Clears all caches
    @CacheEvict(value = {"users", "userSearch", "filteredUsers", "user"}, allEntries = true)
    @Transactional
    public void bulkPermanentDeleteUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("No user IDs provided");
        }

        for (Long id : ids) {
            try {
                permanentDelete(id);
            } catch (Exception e) {
                System.err.println("Failed to permanently delete user " + id + ": " + e.getMessage());
            }
        }
    }

    // ============= COUNT OPERATIONS (NO CACHE - Fast queries) =============

    // Count active users
    public long countActiveUsers() {
        return userRepository.countByIsDeleted(false);
    }

    // Count deleted users
    public long countDeletedUsers() {
        return userRepository.countByIsDeleted(true);
    }

    // Get total count with filters (for pagination metadata)
    public long getTotalCount(String search, String gender, String department) {
        return countActiveUsers();
    }
}
