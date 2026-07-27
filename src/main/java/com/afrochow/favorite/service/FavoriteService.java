package com.afrochow.favorite.service;

import com.afrochow.common.enums.FavoriteType;
import com.afrochow.common.exceptions.DuplicateResourceException;
import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.favorite.dto.FavoriteRequestDto;
import com.afrochow.favorite.dto.FavoriteResponseDto;
import com.afrochow.favorite.model.Favorite;
import com.afrochow.favorite.repository.FavoriteRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

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
    private final OutboxEventService outboxEventService;
    private final StringRedisTemplate redisTemplate;

    private static final Duration VENDOR_FAVOURITE_NOTIFICATION_COOLDOWN = Duration.ofMinutes(10);
    private static final String VENDOR_FAVOURITE_NOTIFICATION_KEY_PREFIX = "afrochow:favorites:vendor-notified:";

    /**
     * Add a favorite (vendor or product)
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "favoriteVendorCounts", allEntries = true),
            @CacheEvict(value = "favoriteProductCounts", allEntries = true)
    })
    public FavoriteResponseDto addFavorite(String username, FavoriteRequestDto request) {
        // Get customer
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        // Validate request
        validateFavoriteRequest(request);

        // Check if already favorite
        if (request.getFavoriteType() == FavoriteType.VENDOR) {
            VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

            if (favoriteRepository.existsByCustomerAndVendor(customer, vendor)) {
                throw new DuplicateResourceException("Vendor is already in favorites");
            }

            // Create favorite
            Favorite favorite = Favorite.builder()
                    .customer(customer)
                    .favoriteType(FavoriteType.VENDOR)
                    .vendor(vendor)
                    .build();

            Favorite savedFavorite;
            try {
                savedFavorite = favoriteRepository.save(favorite);
            } catch (DataIntegrityViolationException ex) {
                // The existsBy check above is a fast-path convenience — it does not
                // close the race window between two concurrent requests for the same
                // customer+vendor pair. The DB's unique constraint is the real guard;
                // if it trips, treat it the same as the pre-check (409, not 500).
                throw new DuplicateResourceException("Vendor is already in favorites");
            }

            // Notify vendor (in-app only) — suppressed if we already notified them
            // about this same customer within the cooldown window.
            if (tryAcquireVendorFavouriteNotificationCooldown(customer.getCustomerProfileId(), vendor.getId())) {
                registerVendorFavouriteCooldownRollbackCleanup(customer.getCustomerProfileId(), vendor.getId());
                outboxEventService.vendorFavourited(
                        vendor.getUser().getPublicUserId(),
                        customer.getUser().getFirstName() + " " + customer.getUser().getLastName()
                );
            } else {
                log.debug("Suppressed duplicate vendor-favourited notification for customer {} / vendor {} (within cooldown)",
                        customer.getCustomerProfileId(), vendor.getId());
            }

            log.info("Customer {} added vendor {} to favorites", customer.getCustomerProfileId(), vendor.getId());
            return toResponseDto(savedFavorite);
        } else {
            Product product = productRepository.findByPublicProductId(request.getProductPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (favoriteRepository.existsByCustomerAndProduct(customer, product)) {
                throw new DuplicateResourceException("Product is already in favorites");
            }

            // Create favorite
            Favorite favorite = Favorite.builder()
                    .customer(customer)
                    .favoriteType(FavoriteType.PRODUCT)
                    .product(product)
                    .build();

            Favorite savedFavorite;
            try {
                savedFavorite = favoriteRepository.save(favorite);
            } catch (DataIntegrityViolationException ex) {
                // Same race-window rationale as the VENDOR branch above — the
                // unique constraint on (customer, product) is the source of truth.
                throw new DuplicateResourceException("Product is already in favorites");
            }

            log.info("Customer {} added product {} to favorites", customer.getCustomerProfileId(), product.getProductId());
            return toResponseDto(savedFavorite);
        }
    }

    /**
     * Remove a favorite (vendor or product)
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "favoriteVendorCounts", allEntries = true),
            @CacheEvict(value = "favoriteProductCounts", allEntries = true)
    })
    public void removeFavorite(String username, FavoriteRequestDto request) {
        // Get customer
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        // Validate request
        validateFavoriteRequest(request);

        if (request.getFavoriteType() == FavoriteType.VENDOR) {
            VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

            if (!favoriteRepository.existsByCustomerAndVendor(customer, vendor)) {
                throw new ResourceNotFoundException("Vendor is not in favorites");
            }

            favoriteRepository.deleteByCustomerAndVendor(customer, vendor);
            log.info("Customer {} removed vendor {} from favorites", customer.getCustomerProfileId(), vendor.getId());
        } else {
            Product product = productRepository.findByPublicProductId(request.getProductPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (!favoriteRepository.existsByCustomerAndProduct(customer, product)) {
                throw new ResourceNotFoundException("Product is not in favorites");
            }

            favoriteRepository.deleteByCustomerAndProduct(customer, product);
            log.info("Customer {} removed product {} from favorites", customer.getCustomerProfileId(), product.getProductId());
        }
    }

    /**
     * Get all favorites for a customer
     */
    @Transactional(readOnly = true)
    public Page<FavoriteResponseDto> getAllFavorites(String username, Pageable pageable) {
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        return favoriteRepository.findByCustomerOrderByCreatedAtDesc(customer, pageable)
                .map(this::toResponseDto);
    }

    /**
     * Get favorites of a specific type
     */
    @Transactional(readOnly = true)
    public Page<FavoriteResponseDto> getFavoritesByType(String username, FavoriteType favoriteType, Pageable pageable) {
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        return favoriteRepository.findByCustomerAndFavoriteTypeOrderByCreatedAtDesc(
                        customer, favoriteType, pageable)
                .map(this::toResponseDto);
    }

    /**
     * Check if a vendor is favorite by customer
     */
    @Transactional(readOnly = true)
    public boolean isVendorFavorited(String username, String vendorPublicId) {
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        return favoriteRepository.existsByCustomerAndVendor(customer, vendor);
    }

    /**
     * Check if a product is favorite by customer
     */
    @Transactional(readOnly = true)
    public boolean isProductFavorited(String username, String productPublicId) {
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        Product product = productRepository.findByPublicProductId(productPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return favoriteRepository.existsByCustomerAndProduct(customer, product);
    }

    /**
     * Get vendor's total favorite count
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "favoriteVendorCounts", key = "#vendorPublicId")
    public Long getVendorFavoriteCount(String vendorPublicId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(vendorPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        return favoriteRepository.countByVendor(vendor);
    }

    /**
     * Get product's total favorite count
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "favoriteProductCounts", key = "#productPublicId")
    public Long getProductFavoriteCount(String productPublicId) {
        Product product = productRepository.findByPublicProductId(productPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return favoriteRepository.countByProduct(product);
    }

    /**
     * Get customer's total favorite count
     */
    @Transactional(readOnly = true)
    public Long getCustomerFavoriteCount(String username) {
        // authentication.getName() resolves to CustomUserDetails.getUsername(), which
        // returns User.getUsername() (a separate generated handle) — NOT the email.
        // findByEmail(username) would silently never match, so we use the same
        // username-or-email lookup ReviewService.resolveUser() relies on.
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CustomerProfile customer = customerProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));

        return favoriteRepository.countByCustomer(customer);
    }

    // ========== HELPER METHODS ==========

    /**
     * Returns true and starts a shared Redis TTL cooldown the first time this
     * customer+vendor pair is seen, or once the previous cooldown has expired.
     * Returns false without resetting the window while a notification for this
     * pair is still fresh. If Redis is temporarily unavailable, prefer delivery
     * over suppression because a rare duplicate notification is less harmful than
     * silently missing a legitimate vendor notification.
     */
    private boolean tryAcquireVendorFavouriteNotificationCooldown(Long customerProfileId, Long vendorId) {
        String key = vendorFavouriteNotificationCooldownKey(customerProfileId, vendorId);
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", VENDOR_FAVOURITE_NOTIFICATION_COOLDOWN);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            log.warn("favorite.vendor_notification_cooldown.redis_unavailable key={}", key, ex);
            return true;
        }
    }

    private void registerVendorFavouriteCooldownRollbackCleanup(Long customerProfileId, Long vendorId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        String key = vendorFavouriteNotificationCooldownKey(customerProfileId, vendorId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        redisTemplate.delete(key);
                    } catch (Exception ex) {
                        log.warn("favorite.vendor_notification_cooldown.rollback_cleanup_failed key={}", key, ex);
                    }
                }
            }
        });
    }

    private String vendorFavouriteNotificationCooldownKey(Long customerProfileId, Long vendorId) {
        return VENDOR_FAVOURITE_NOTIFICATION_KEY_PREFIX + customerProfileId + ":" + vendorId;
    }

    /**
     * Validate favorite request
     */
    private void validateFavoriteRequest(FavoriteRequestDto request) {
        if (request.getFavoriteType() == FavoriteType.VENDOR) {
            if (request.getVendorPublicId() == null || request.getVendorPublicId().isBlank()) {
                throw new IllegalArgumentException("Vendor ID is required for VENDOR favorite type");
            }
            if (request.getProductPublicId() != null) {
                throw new IllegalArgumentException("Product ID must be null for VENDOR favorite type");
            }
        } else if (request.getFavoriteType() == FavoriteType.PRODUCT) {
            if (request.getProductPublicId() == null || request.getProductPublicId().isBlank()) {
                throw new IllegalArgumentException("Product ID is required for PRODUCT favorite type");
            }
            if (request.getVendorPublicId() != null) {
                throw new IllegalArgumentException("Vendor ID must be null for PRODUCT favorite type");
            }
        }
    }

    /**
     * Convert Favorite entity to response DTO
     */
    private FavoriteResponseDto toResponseDto(Favorite favorite) {
        FavoriteResponseDto.FavoriteResponseDtoBuilder builder = FavoriteResponseDto.builder()
                .publicFavoriteId(favorite.getPublicFavoriteId())
                .favoriteType(favorite.getFavoriteType())
                .createdAt(favorite.getCreatedAt());

        if (favorite.getFavoriteType() == FavoriteType.VENDOR && favorite.getVendor() != null) {
            VendorProfile vendor = favorite.getVendor();
            builder.vendor(FavoriteResponseDto.VendorBasicInfo.builder()
                    .publicVendorId(vendor.getPublicVendorId())
                    .restaurantName(vendor.getRestaurantName())
                    .logoUrl(vendor.getLogoUrl())
                    .storeCategory(vendor.getStoreCategory())
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
