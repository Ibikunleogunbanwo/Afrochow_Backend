package com.afrochow.vendor.model;

import com.afrochow.address.model.Address;
import com.afrochow.favorite.model.Favorite;
import com.afrochow.order.model.Order;
import com.afrochow.product.model.Product;
import com.afrochow.promotion.model.Promotion;
import com.afrochow.review.model.Review;
import com.afrochow.user.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.VendorStatus;

@Entity
@Table(name = "vendor_profile", indexes = {
        @Index(name = "idx_is_verified", columnList = "isVerified"),
        @Index(name = "idx_store_category", columnList = "cuisineType")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"user", "products", "orders", "reviews"})
public class VendorProfile {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== LINK TO USER (ONE-TO-ONE) ==========
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private User user;

    // ========== RESTAURANT INFORMATION ==========
    @Column(nullable = false, length = 100)
    private String restaurantName;

    @Column(length = 1000)
    private String description;

    // Column name kept as "cuisineType" to avoid a DB migration.
    @Column(name = "cuisineType", length = 50)
    private String storeCategory;

    private String logoUrl;
    private String bannerUrl;

    // ========== STRIPE CONNECT ==========
    @Column(length = 100)
    private String stripeAccountId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean stripeOnboardingComplete = false;

    // ========== VENDOR STATUS (STATE MACHINE) ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VendorStatus vendorStatus = VendorStatus.PENDING_PROFILE;

    // ========== BUSINESS VERIFICATION ==========
    @Column(length = 255)
    private String businessLicenseUrl;

    @Column(length = 50)
    private String taxId;

    /**
     * @deprecated Use {@link #vendorStatus} instead.
     * Retained for backward compatibility during migration period.
     * Will be removed once all references are updated to use VendorStatus.
     */
    @Deprecated
    @Column(nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * @deprecated Use {@link #vendorStatus} instead.
     * Retained for backward compatibility during migration period.
     */
    @Deprecated
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime verifiedAt;

    // ========== FOOD HANDLING CERTIFICATION (Canada) ==========
    /**
     * URL to the uploaded food handling certificate document (PDF or image).
     * Province-specific certs: FoodSafe (BC), Smart Serve (ON), etc.
     */
    @Column(length = 500)
    private String foodHandlingCertUrl;

    /**
     * Certificate number as printed on the document, for cross-reference.
     */
    @Column(length = 100)
    private String foodHandlingCertNumber;

    /**
     * Issuing body, e.g. "FoodSafe BC", "Manitoba Food Handler", "CFIA".
     */
    @Column(length = 150)
    private String foodHandlingCertIssuingBody;

    /**
     * Expiry date of the certificate. Most Canadian food handler certs expire after 5 years.
     * Null if not yet uploaded or not applicable.
     */
    private LocalDateTime foodHandlingCertExpiry;

    /**
     * Timestamp when an admin confirmed the certificate is valid.
     */
    private LocalDateTime certVerifiedAt;

    /**
     * Public user ID of the admin who verified the certificate.
     */
    @Column(length = 36)
    private String certVerifiedByAdminId;

    // ========== TIMEZONE ==========
    @Column(length = 50)
    @Builder.Default
    private String timezone = "America/Edmonton";

    // ========== OPERATING HOURS (JSON) ==========
    @Column(columnDefinition = "TEXT")
    private String operatingHoursJson;

    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transient
    private static final DateTimeFormatter HOURS_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    @Transient
    public Map<String, DayHours> getOperatingHours() {
        if (operatingHoursJson == null || operatingHoursJson.isEmpty()) {
            return getDefaultOperatingHours();
        }
        try {
            Map<String, DayHours> raw = objectMapper.readValue(operatingHoursJson,
                    new TypeReference<Map<String, DayHours>>() {});
            // Normalize keys to lowercase so lookups always work regardless of how
            // data was saved (frontend may have stored "Monday" instead of "monday").
            Map<String, DayHours> normalized = new HashMap<>();
            raw.forEach((k, v) -> normalized.put(k.toLowerCase(), v));
            return normalized;
        } catch (JsonProcessingException e) {
            return getDefaultOperatingHours();
        }
    }

    @Transient
    public void setOperatingHours(Map<String, DayHours> operatingHours) {
        try {
            // Always persist with lowercase keys to keep data consistent.
            Map<String, DayHours> normalized = new HashMap<>();
            operatingHours.forEach((k, v) -> normalized.put(k.toLowerCase(), v));
            this.operatingHoursJson = objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize operating hours", e);
        }
    }

    @Transient
    private Map<String, DayHours> getDefaultOperatingHours() {
        Map<String, DayHours> defaultHours = new HashMap<>();
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

        for (String day : days) {
            DayHours hours = new DayHours();
            hours.setIsOpen(!day.equals("sunday"));
            hours.setOpenTime("09:00");
            hours.setCloseTime("22:00");
            defaultHours.put(day, hours);
        }

        return defaultHours;
    }

    // ========== SERVICE OPTIONS ==========
    @Column(nullable = false)
    @Builder.Default
    private Boolean offersDelivery = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean offersPickup = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer preparationTime = 30;

    // ========== DELIVERY SETTINGS ==========
    @Column(precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;

    private Integer estimatedDeliveryMinutes;

    @Column(precision = 5, scale = 1)
    private BigDecimal maxDeliveryDistanceKm;

    // ========== ADDRESS ==========
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    // ========== STATISTICS ==========
    @Column(nullable = false)
    @Builder.Default
    private Integer totalOrdersCompleted = 0;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    // ========== TIMESTAMPS ==========
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ========== RELATIONSHIPS ==========
    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Promotion> promotions = new ArrayList<>();

    // ========== HELPER METHODS ==========

    @Transient
    public String getPublicVendorId() {
        return user != null ? user.getPublicUserId() : null;
    }

    @Transient
    public Double getAverageRating() {
        if (reviews == null || reviews.isEmpty()) return 0.0;
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
    }

    @Transient
    public int getReviewCount() {
        return reviews != null ? reviews.size() : 0;
    }

    @Transient
    private ZoneId getVendorZone() {
        try {
            return ZoneId.of(timezone != null ? timezone : "America/Edmonton");
        } catch (Exception e) {
            return ZoneId.of("America/Edmonton");
        }
    }

    public static String getTimezoneFromProvince(Province province) {
        if (province == null) return "America/Edmonton";

        return switch (province) {
            case BC -> "America/Vancouver";
            case AB -> "America/Edmonton";
            case SK -> "America/Regina";
            case MB -> "America/Winnipeg";
            case ON -> "America/Toronto";
            case QC -> "America/Montreal";
            case NB -> "America/Moncton";
            case NS -> "America/Halifax";
            case PE -> "America/Halifax";
            case NL -> "America/St_Johns";
            case NT -> "America/Yellowknife";
            case NU -> "America/Rankin_Inlet";
            case YT -> "America/Whitehorse";
            default -> "America/Edmonton";
        };
    }

    @Transient
    public boolean isOpenNow() {
        if (vendorStatus != VendorStatus.PROVISIONAL && vendorStatus != VendorStatus.VERIFIED) return false;

        Map<String, DayHours> hours = getOperatingHours();
        if (hours == null || hours.isEmpty()) return false;

        ZoneId vendorZone = getVendorZone();
        DayOfWeek today = DayOfWeek.from(LocalDateTime.now(vendorZone));
        String dayKey = today.name().toLowerCase();
        LocalTime now = LocalTime.now(vendorZone);

        DayHours todayHours = hours.get(dayKey);
        if (todayHours == null || !todayHours.getIsOpen()) return false;

        try {
            LocalTime openTime  = LocalTime.parse(todayHours.getOpenTime());
            LocalTime closeTime = LocalTime.parse(todayHours.getCloseTime());

            // Handle overnight hours e.g. 10:00 PM - 02:00 AM
            if (closeTime.isBefore(openTime)) {
                return now.isAfter(openTime) || now.isBefore(closeTime);
            }

            return now.isAfter(openTime) && now.isBefore(closeTime);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns today's hours formatted as "hh:mm AM - hh:mm PM" (12-hour, AM/PM).
     * Matches the regex expected by the frontend's computeIsOpenNow() helper.
     * Returns "Closed today" if the vendor is marked closed, "Hours not set" as fallback.
     */
    @Transient
    public String getTodayHoursFormatted() {
        Map<String, DayHours> hours = getOperatingHours();
        if (hours == null || hours.isEmpty()) return "Hours not set";

        ZoneId vendorZone = getVendorZone();
        DayOfWeek today = DayOfWeek.from(LocalDateTime.now(vendorZone));
        String dayKey = today.name().toLowerCase();

        DayHours todayHours = hours.get(dayKey);
        if (todayHours == null || !todayHours.getIsOpen()) return "Closed today";

        try {
            String open  = LocalTime.parse(todayHours.getOpenTime()).format(HOURS_FORMATTER);
            String close = LocalTime.parse(todayHours.getCloseTime()).format(HOURS_FORMATTER);
            return String.format("%s - %s", open, close);
        } catch (Exception e) {
            return "Hours not set";
        }
    }

    @Transient
    public boolean hasOperatingDays() {
        Map<String, DayHours> hours = getOperatingHours();
        if (hours == null || hours.isEmpty()) return false;

        return hours.values().stream()
                .anyMatch(day -> day != null && day.getIsOpen());
    }

    @Transient
    public String getFullAddress() {
        return address != null ? address.getFullAddress() : "";
    }

    @Transient
    public boolean hasActiveProducts() {
        return products != null && products.stream().anyMatch(Product::getAvailable);
    }

    public void addProduct(Product product) {
        products.add(product);
        product.setVendor(this);
    }

    public boolean removeProduct(Product product) {
        if (products.remove(product)) {
            product.setVendor(null);
            return true;
        }
        return false;
    }

    @Transient
    public boolean isVendor() {
        return user != null && user.isVendor();
    }

    public void recordCompletedOrder(BigDecimal orderAmount) {
        this.totalOrdersCompleted++;
        this.totalRevenue = this.totalRevenue.add(orderAmount);
    }

    // ========== VALIDATION HELPERS ==========

    @Transient
    public boolean canReceiveOrders() {
        return (vendorStatus == VendorStatus.PROVISIONAL || vendorStatus == VendorStatus.VERIFIED)
                && hasOperatingDays()
                && hasActiveProducts();
    }

    /**
     * Returns true if the vendor is in a provisional state — live but cert not yet verified.
     * A daily order cap should be enforced for provisional vendors.
     */
    @Transient
    public boolean isProvisional() {
        return vendorStatus == VendorStatus.PROVISIONAL;
    }

    @Transient
    public boolean isFullyVerified() {
        return vendorStatus == VendorStatus.VERIFIED;
    }

    @Transient
    public boolean hasFoodHandlingCert() {
        return foodHandlingCertUrl != null && !foodHandlingCertUrl.isBlank();
    }

    @Transient
    public boolean isCertExpired() {
        return foodHandlingCertExpiry != null && foodHandlingCertExpiry.isBefore(LocalDateTime.now());
    }

    @Transient
    public boolean canReceivePayouts() {
        return vendorStatus == VendorStatus.VERIFIED && taxId != null && !taxId.isEmpty();
    }

    // ========== INNER CLASS FOR DAY HOURS ==========

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class DayHours {
        private Boolean isOpen;
        private String openTime;
        private String closeTime;
    }
}
