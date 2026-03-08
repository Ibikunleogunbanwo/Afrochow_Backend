package com.afrochow.customer.model;

import com.afrochow.address.model.Address;
import com.afrochow.order.model.Order;
import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"addresses", "orders"})
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerProfileId;

    // ==========================================
// USER RELATION (ONE-TO-ONE)
// ==========================================
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ==========================================
// CUSTOMER-SPECIFIC FIELDS
// ==========================================
    @Column(length = 500)
    private String defaultDeliveryInstructions;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CREDIT_CARD;

    @Builder.Default
    private Integer loyaltyPoints = 0;


    // ==========================================
// TIMESTAMPS
// ==========================================
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ==========================================
// RELATIONSHIPS
// ==========================================
    @OneToMany(mappedBy = "customerProfile",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();


    @OneToMany(mappedBy = "customer",
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    // ==========================================
// HELPER METHODS
// ==========================================
    @Transient
    public int getTotalOrders() {
        return orders != null ? orders.size() : 0;
    }

    @Transient
    public String getPublicCustomerId() {
        return user != null ? user.getPublicUserId() : null;
    }

    // ==========================================
// DEFAULT ADDRESS LOGIC
// ==========================================
    @Transient
    public Address getDefaultAddress() {
        if (addresses == null || addresses.isEmpty()) return null;

        return addresses.stream()
                .filter(Address::getDefaultAddress)
                .findFirst()
                .orElse(addresses.getFirst());
    }

    public void addAddress(Address address) {
        if (address == null) return;

        // If adding as default, reset other defaults
        if (address.getDefaultAddress() != null && address.getDefaultAddress()) {
            addresses.forEach(a -> a.setDefaultAddress(false));
        }

        addresses.add(address);
        address.setCustomerProfile(this);

        // The First address automatically becomes default if none set
        if (addresses.size() == 1 && !address.getDefaultAddress()) {
            address.setDefaultAddress(true);
        }
    }


    public void removeAddress(Address address) {
        if (address == null) return;

        boolean removed = addresses.remove(address);
        if (!removed) return;

        address.setCustomerProfile(null);

        // Reassign default if needed
        if (address.getDefaultAddress() && !addresses.isEmpty()) {
            addresses.getFirst().setDefaultAddress(true);
        }
    }

    // ==========================================
// ROLE CHECK
// ==========================================
    @Transient
    public boolean isCustomer() {
        return user != null && user.isCustomer();
    }


}
