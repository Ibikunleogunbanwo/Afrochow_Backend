package com.afrochow.address.model;

import com.afrochow.common.enums.Province;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.vendor.model.VendorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Locale;
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

    private static final String PUBLIC_ID_PREFIX = "ADDR-";
    private static final String DEFAULT_COUNTRY = "Canada";

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
    private String country = DEFAULT_COUNTRY;

    private Double latitude;

    private Double longitude;

    @Builder.Default
    private Boolean defaultAddress = false;

    // Distinguishes demo/seed addresses (see CompleteFinalSeeder) from real
    // customer/vendor addresses. Real address creation never sets this true.
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSeedData = false;

    // Relationships

    /**
     * Customer addresses are owned by the address table through
     * address.customer_profile_id. A customer can have many addresses.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_profile_id")
    private CustomerProfile customerProfile;

    /**
     * Vendor addresses are owned from VendorProfile through vendor_profile.address_id.
     * This side is read through mappedBy and does not create a vendor_id column here.
     */
    @OneToOne(mappedBy = "address", fetch = FetchType.LAZY)
    private VendorProfile vendor;

    // Timestamps

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Lifecycle

    @PrePersist
    public void generatePublicId() {
        if (publicAddressId == null) {
            publicAddressId = PUBLIC_ID_PREFIX + UUID.randomUUID();
        }
    }

    // Derived properties

    @Transient
    public String getFullAddress() {
        return String.format(
                "%s, %s, %s, %s",
                valueOrEmpty(addressLine),
                valueOrEmpty(city),
                valueOrEmpty(postalCode),
                valueOrEmpty(country)
        );
    }

    @Transient
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(valueOrEmpty(addressLine)).append(", ").append(valueOrEmpty(city));
        if (province != null) {
            sb.append(", ").append(province);
        }
        sb.append(" ").append(valueOrEmpty(postalCode)).append(", ").append(valueOrEmpty(country));
        return sb.toString();
    }

    // Normalization

    public void setPostalCode(String postalCode) {
        this.postalCode = normalizePostalCode(postalCode);
    }

    public static class AddressBuilder {
        public AddressBuilder postalCode(String postalCode) {
            this.postalCode = normalizePostalCode(postalCode);
            return this;
        }
    }

    private static String normalizePostalCode(String postalCode) {
        return postalCode == null
                ? null
                : postalCode.toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
