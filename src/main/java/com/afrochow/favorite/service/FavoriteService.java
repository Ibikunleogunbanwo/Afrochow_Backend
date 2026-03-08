package com.afrochow.favorite.service;

import com.afrochow.common.enums.FavoriteType;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.favorite.dto.FavoriteRequestDto;
import com.afrochow.favorite.dto.FavoriteResponseDto;
import com.afrochow.favorite.model.Favorite;
import com.afrochow.favorite.repository.FavoriteRepository;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing customer favorites (vendors and products)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Add a favorite (vendor or product)
     */
    @Transactional
    public FavoriteResponseDto addFavorite(String username, FavoriteRequestDto request) {
        // Get customer
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        // Validate request
        validateFavoriteRequest(request);

        // Check if already favorite
        if (request.getFavoriteType() == FavoriteType.VENDOR) {
            VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                    .orElseThrow(() -> new RuntimeException("Vendor not found"));

            if (favoriteRepository.existsByCustomerAndVendor(customer, vendor)) {
                throw new RuntimeException("Vendor is already in favorites");
            }

            // Create favorite
            Favorite favorite = Favorite.builder()
                    .customer(customer)
                    .favoriteType(FavoriteType.VENDOR)
                    .vendor(vendor)
                    .build();

            Favorite savedFavorite = favoriteRepository.save(favorite);

            // Notify vendor (in-app only)
            notificationService.notifyVendorFavorited(
                    vendor.getUser().getPublicUserId(),
                    customer.getUser().getFirstName() + " " + customer.getUser().getLastName()
            );

            log.info("Customer {} added vendor {} to favorites", customer.getCustomerProfileId(), vendor.getId());
            return toResponseDto(savedFavorite);
        } else {
            Product product = productRepository.findByPublicProductId(request.getProductPublicId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (favoriteRepository.existsByCustomerAndProduct(customer, product)) {
                throw new RuntimeException("Product is already in favorites");
            }

            // Create favorite
            Favorite favorite = Favorite.builder()
                    .customer(customer)
                    .favoriteType(FavoriteType.PRODUCT)
                    .product(product)
                    .build();

            Favorite savedFavorite = favoriteRepository.save(favorite);

            log.info("Customer {} added product {} to favorites", customer.getCustomerProfileId(), product.getProductId());
            return toResponseDto(savedFavorite);
        }
    }

    /**
     * Remove a favorite (vendor or product)
     */
    @Transactional
    public void removeFavorite(String username, FavoriteRequestDto request) {
        // Get customer
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        // Validate request
        validateFavoriteRequest(request);

        if (request.getFavoriteType() == FavoriteType.VENDOR) {
            VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                    .orElseThrow(() -> new RuntimeException("Vendor not found"));

            if (!favoriteRepository.existsByCustomerAndVendor(customer, vendor)) {
                throw new RuntimeException("Vendor is not in favorites");
            }

            favoriteRepository.deleteByCustomerAndVendor(customer, vendor);
            log.info("Customer {} removed vendor {} from favorites", customer.getCustomerProfileId(), vendor.getId());
        } else {
            Product product = productRepository.findByPublicProductId(request.getProductPublicId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (!favoriteRepository.existsByCustomerAndProduct(customer, product)) {
                throw new RuntimeException("Product is not in favorites");
            }

            favoriteRepository.deleteByCustomerAndProduct(customer, product);
            log.info("Customer {} removed product {} from favorites", customer.getCustomerProfileId(), product.getProductId());
        }
    }

    /**
     * Get all favorites for a customer
     */
    @Transactional(readOnly = true)
    public List<FavoriteResponseDto> getAllFavorites(String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        List<Favorite> favorites = favoriteRepository.findByCustomerOrderByCreatedAtDesc(customer);
        return favorites.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get favorites of a specific type
     */
    @Transactional(readOnly = true)
    public List<FavoriteResponseDto> getFavoritesByType(String username, FavoriteType favoriteType) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        List<Favorite> favorites = favoriteRepository.findByCustomerAndFavoriteTypeOrderByCreatedAtDesc(
                customer, favoriteType);
        return favorites.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Check if a vendor is favorite by customer
     */
    @Transactional(readOnly = true)
    public boolean isVendorFavorited(String username, String vendorPublicId) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        return favoriteRepository.existsByCustomerAndVendor(customer, vendor);
    }

    /**
     * Check if a product is favorite by customer
     */
    @Transactional(readOnly = true)
    public boolean isProductFavorited(String username, String productPublicId) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        Product product = productRepository.findByPublicProductId(productPublicId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return favoriteRepository.existsByCustomerAndProduct(customer, product);
    }

    /**
     * Get vendor's total favorite count
     */
    @Transactional(readOnly = true)
    public Long getVendorFavoriteCount(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        return favoriteRepository.countByVendor(vendor);
    }

    /**
     * Get product's total favorite count
     */
    @Transactional(readOnly = true)
    public Long getProductFavoriteCount(String productPublicId) {
        Product product = productRepository.findByPublicProductId(productPublicId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return favoriteRepository.countByProduct(product);
    }

    /**
     * Get customer's total favorite count
     */
    @Transactional(readOnly = true)
    public Long getCustomerFavoriteCount(String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Customer profile not found"));

        return favoriteRepository.countByCustomer(customer);
    }

    // ========== HELPER METHODS ==========

    /**
     * Validate favorite request
     */
    private void validateFavoriteRequest(FavoriteRequestDto request) {
        if (request.getFavoriteType() == FavoriteType.VENDOR) {
            if (request.getVendorPublicId() == null || request.getVendorPublicId().isBlank()) {
                throw new RuntimeException("Vendor ID is required for VENDOR favorite type");
            }
            if (request.getProductPublicId() != null) {
                throw new RuntimeException("Product ID must be null for VENDOR favorite type");
            }
        } else if (request.getFavoriteType() == FavoriteType.PRODUCT) {
            if (request.getProductPublicId() == null || request.getProductPublicId().isBlank()) {
                throw new RuntimeException("Product ID is required for PRODUCT favorite type");
            }
            if (request.getVendorPublicId() != null) {
                throw new RuntimeException("Vendor ID must be null for PRODUCT favorite type");
            }
        }
    }

    /**
     * Convert Favorite entity to response DTO
     */
    private FavoriteResponseDto toResponseDto(Favorite favorite) {
        FavoriteResponseDto.FavoriteResponseDtoBuilder builder = FavoriteResponseDto.builder()
                .favoriteId(favorite.getFavoriteId())
                .favoriteType(favorite.getFavoriteType())
                .createdAt(favorite.getCreatedAt());

        if (favorite.getFavoriteType() == FavoriteType.VENDOR && favorite.getVendor() != null) {
            VendorProfile vendor = favorite.getVendor();
            builder.vendor(FavoriteResponseDto.VendorBasicInfo.builder()
                    .publicVendorId(vendor.getPublicVendorId())
                    .restaurantName(vendor.getRestaurantName())
                    .logoUrl(vendor.getLogoUrl())
                    .cuisine(vendor.getCuisineType())
                    .rating(vendor.getAverageRating())
                    .isActive(vendor.getIsActive())
                    .build());
        } else if (favorite.getFavoriteType() == FavoriteType.PRODUCT && favorite.getProduct() != null) {
            Product product = favorite.getProduct();
            builder.product(FavoriteResponseDto.ProductBasicInfo.builder()
                    .publicProductId(product.getPublicProductId())
                    .productName(product.getName())
                    .imageUrl(product.getImageUrl())
                    .price(product.getPrice().doubleValue())
                    .isAvailable(product.getAvailable())
                    .vendorName(product.getVendor().getRestaurantName())
                    .vendorPublicId(product.getVendor().getPublicVendorId())
                    .build());
        }

        return builder.build();
    }
}
