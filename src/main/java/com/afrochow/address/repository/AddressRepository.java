package com.afrochow.address.repository;

import com.afrochow.address.model.Address;
import com.afrochow.customer.model.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByPublicAddressId(String publicAddressId);

    List<Address> findByCustomerProfile(CustomerProfile profile);

    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.customerProfile.customerProfileId = :customerId AND a.defaultAddress = true")
    void unsetDefaultForCustomer(@Param("customerId") Long customerId);

    Optional<Address> findByCustomerProfileAndDefaultAddress(CustomerProfile profile, boolean defaultAddress);
}
