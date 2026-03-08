package com.afrochow.user.service;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.Role;
import com.afrochow.user.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ========== CACHE EVICTION METHODS ==========

    @Transactional
    @CacheEvict(value = "users", key = "#user.email")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void updatePassword(String email, String newPassword) {
        User user = findByEmail(email);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void updateRole(String email, Role newRole) {
        User user = findByEmail(email);
        user.setRole(newRole);
        userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public void toggleActiveStatus(String email) {
        User user = findByEmail(email);
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsersCache() {
    }

    // ========== READ-ONLY METHODS (no cache eviction) ==========

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    public User findByPublicUserId(String publicUserId) {
        return userRepository.findByPublicUserId(publicUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + publicUserId));
    }
}