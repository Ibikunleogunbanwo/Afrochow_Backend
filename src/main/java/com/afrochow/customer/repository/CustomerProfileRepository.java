package com.afrochow.customer.repository;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    Optional<CustomerProfile> findByUser(User user);

    Optional<CustomerProfile> findByUser_UserId(Long userId);

    Optional<CustomerProfile> findByUser_PublicUserId(String publicUserId);


    @Query("SELECT cp FROM CustomerProfile cp ORDER BY SIZE(cp.orders) DESC")
    List<CustomerProfile> findTopCustomers();


    Long countBy();
}