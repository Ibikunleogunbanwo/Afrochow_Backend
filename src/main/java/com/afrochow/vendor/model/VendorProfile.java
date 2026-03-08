package com.afrochow.vendor.model;

import com.afrochow.address.model.Address;
import com.afrochow.order.model.Order;
import com.afrochow.product.model.Product;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "vendor_profile", indexes = {
        @Index(name = "idx_is_verified", columnList = "isVerified"),
        @Index(name = "idx_cuisine_type", columnList = "cuisineType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorProfile {

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

    @Column(length = 50)
    private String cuisineType;

    private String logoUrl;
    private String bannerUrl;

    // ========== BUSINESS VERIFICATION ==========
    @Column(length = 255)
    private String businessLicenseUrl;

    @Column(length = 50)
    private String taxId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime verifiedAt;

    // ========== OPERATING HOURS (JSON) ==========
    // Stores weekly schedule as JSON
    // Format: {"Monday": {"isOpen": true, "openTime": "09:00", "closeTime": "22:00"}, ...}
    // Using TEXT for maximum MySQL compatibility (works on 5.5, 5.6, 5.7, 8.0+)
    @Column(columnDefinition = "TEXT")
    private String operatingHoursJson;

    // Helper to get/set operating hours as Map
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transient
    public Map<String, DayHours> getOperatingHours() {
        if (operatingHoursJson == null || operatingHoursJson.isEmpty()) {
            return getDefaultOperatingHours();
        }
        try {
            return objectMapper.readValue(operatingHoursJson,
                    new TypeReference<Map<String, DayHours>>() {});
        } catch (JsonProcessingException e) {
            return getDefaultOperatingHours();
        }
    }


    @Transient
    public void setOperatingHours(Map<String, DayHours> operatingHours) {
        try {
            this.operatingHoursJson = objectMapper.writeValueAsString(operatingHours);
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
            hours.setIsOpen(!day.equals("sunday")); // Default closed on Sunday
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
    private Integer preparationTime = 30; // in minutes

    // ========== DELIVERY SETTINGS ==========
    @Column(precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;

    private Integer estimatedDeliveryMinutes;

    @Column(precision = 5, scale = 1)
    private BigDecimal maxDeliveryDistanceKm;

    // ========== ADDRESS ==========
    @OneToOne(cascade = CascadeType.ALL)
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
    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

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

    /**
     * Check if vendor is currently open based on operating hours
     */
    @Transient
    public boolean isOpenNow() {
        if (!isActive) return false;

        Map<String, DayHours> hours = getOperatingHours();
        if (hours == null || hours.isEmpty()) return false;

        // Get current day and time
        DayOfWeek today = DayOfWeek.from(LocalDateTime.now());
        String dayKey = today.name().toLowerCase();
        LocalTime now = LocalTime.now();

        DayHours todayHours = hours.get(dayKey);
        if (todayHours == null || !todayHours.getIsOpen()) {
            return false;
        }

        try {
            LocalTime openTime = LocalTime.parse(todayHours.getOpenTime());
            LocalTime closeTime = LocalTime.parse(todayHours.getCloseTime());


            if (closeTime.isBefore(openTime)) {
                return now.isAfter(openTime) || now.isBefore(closeTime);
            }

            return now.isAfter(openTime) && now.isBefore(closeTime);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get today's operating hours as formatted string
     */
    @Transient
    public String getTodayHoursFormatted() {
        Map<String, DayHours> hours = getOperatingHours();
        if (hours == null || hours.isEmpty()) return "Hours not set";

        DayOfWeek today = DayOfWeek.from(LocalDateTime.now());
        String dayKey = today.name().toLowerCase();

        DayHours todayHours = hours.get(dayKey);
        if (todayHours == null || !todayHours.getIsOpen()) {
            return "Closed today";
        }

        return String.format("Open %s - %s",
                todayHours.getOpenTime(),
                todayHours.getCloseTime());
    }

    /**
     * Check if vendor has at least one day open
     */
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
        return isActive && isVerified && hasOperatingDays() && hasActiveProducts();
    }

    @Transient
    public boolean canReceivePayouts() {
        return isVerified && taxId != null && !taxId.isEmpty();
    }



    // ========== INNER CLASS FOR DAY HOURS ==========
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayHours {
        private Boolean isOpen;
        private String openTime;   // Format: "HH:MM"
        private String closeTime;  // Format: "HH:MM"
    }
}