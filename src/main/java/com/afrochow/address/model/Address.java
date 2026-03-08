package com.afrochow.address.model;

import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.common.enums.Province;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "address")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"customerProfile", "vendor"})
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    @Column(unique = true, nullable = false, updatable = false)
    private String publicAddressId;

    @Column(nullable = false, length = 200)
    private String addressLine;

    @Column(nullable = false, length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    private Province province;

    @Column(nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String country = "Canada";

    @Builder.Default
    private Boolean defaultAddress = false;

    // =================================================
    // RELATIONSHIPS
    // =================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_profile_id")
    private CustomerProfile customerProfile;

    @OneToOne(mappedBy = "address",
            fetch = FetchType.LAZY)
    private VendorProfile vendor;

    // =================================================
    // TIMESTAMPS
    // =================================================
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // =================================================
    // LIFECYCLE METHODS
    // =================================================
    @PrePersist
    public void generatePublicId() {
        if (this.publicAddressId == null) {
            this.publicAddressId = "ADDR-" + UUID.randomUUID();
        }
    }

    // =================================================
    // DERIVED PROPERTIES
    // =================================================
    @Transient
    public String getFullAddress() {
        return String.format(
                "%s, %s, %s, %s",
                addressLine != null ? addressLine : "",
                city != null ? city : "",
                postalCode != null ? postalCode : "",
                country != null ? country : ""
        );
    }

    @Transient
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine).append(", ").append(city);
        if (province != null) {
            sb.append(", ").append(province);
        }
        sb.append(" ").append(postalCode).append(", ").append(country);
        return sb.toString();
    }

    // =================================================
    // CUSTOM SETTERS / BUILDER NORMALIZATION
    // =================================================
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode == null ? null : postalCode.toUpperCase().replace(" ", "");
    }

    // Normalize postal code when using builder
    public static class AddressBuilder {
        public AddressBuilder postalCode(String postalCode) {
            this.postalCode = postalCode == null ? null : postalCode.toUpperCase().replace(" ", "");
            return this;
        }
    }
}
