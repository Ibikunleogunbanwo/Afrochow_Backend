package com.afrochow.admin.repository;
import com.afrochow.admin.model.AdminProfile;
import com.afrochow.user.model.User;
import com.afrochow.common.enums.AdminAccessLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {

    Optional<AdminProfile> findByUser(User user);

    Optional<AdminProfile> findByUser_PublicUserId(String publicUserId);

    List<AdminProfile> findByAccessLevel(AdminAccessLevel accessLevel);

    List<AdminProfile> findByDepartment(String department);

    Optional<AdminProfile> findByEmployeeId(String employeeId);
}