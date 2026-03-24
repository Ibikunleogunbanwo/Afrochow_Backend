package com.afrochow.seeder;

import com.afrochow.admin.model.AdminProfile;
import com.afrochow.admin.repository.AdminProfileRepository;
import com.afrochow.common.enums.AdminAccessLevel;
import com.afrochow.common.enums.Department;
import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${superadmin.email}")
    private String email;

    @Value("${superadmin.password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        User superAdmin;

        // Step 1: Create user if not exists
        if (userRepository.findByEmail(email).isEmpty()) {
            superAdmin = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(Role.SUPERADMIN)
                    .isActive(true)
                    .firstName("Super")
                    .lastName("Admin")
                    .build();
            userRepository.save(superAdmin);
            System.out.println("✅ SUPERADMIN user seeded.");
        } else {
            superAdmin = userRepository.findByEmail(email).get();
            System.out.println("ℹ️ SUPERADMIN user already exists, skipping user creation.");
        }

        // Step 2: Create admin profile if not exists
        if (adminProfileRepository.findByUser(superAdmin).isEmpty()) {
            AdminProfile profile = AdminProfile.builder()
                    .user(superAdmin)
                    .department(Department.MANAGEMENT)
                    .accessLevel(AdminAccessLevel.SUPER_ADMIN)
                    .canVerifyVendors(true)
                    .canManageUsers(true)
                    .canViewReports(true)
                    .canManagePayments(true)
                    .canManageCategories(true)
                    .canResolveDisputes(true)
                    .build();
            adminProfileRepository.save(profile);
            System.out.println("✅ SUPERADMIN profile seeded.");
        } else {
            System.out.println("ℹ️ SUPERADMIN profile already exists, skipping.");
        }
    }
    }
