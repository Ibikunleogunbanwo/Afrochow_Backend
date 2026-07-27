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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FavoriteService}.
 *
 * Covers the two live bugs that were fixed during the production-readiness
 * pass: (1) the findByEmail vs findByUsernameOrEmail user-resolution bug that
 * caused every authenticated favorite call to 404, and (2) the TOCTOU race
 * between the existsBy pre-check and the actual insert, now guarded by
 * catching DataIntegrityViolationException from the DB's unique constraint.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock private FavoriteRepository favoriteRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private OutboxEventService outboxEventService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FavoriteService favoriteService;

    private static final String USERNAME = "ade123";
    private static final String VENDOR_PUBLIC_ID = "vendor-pub-1";
    private static final String PRODUCT_PUBLIC_ID = "product-pub-1";

    private User user;
    private CustomerProfile customer;
    private VendorProfile vendor;
    private Product product;

    @BeforeEach
    void setUp() {
        User vendorUser = User.builder()
                .userId(2L)
                .publicUserId(VENDOR_PUBLIC_ID)
                .username("vendorhandle")
                .email("vendor@afrochow.com")
                .firstName("Vendor")
                .lastName("Owner")
                .build();

        user = User.builder()
                .userId(1L)
                .publicUserId("user-pub-1")
                .username(USERNAME)
                .email("ade@afrochow.com")
                .firstName("Ade")
                .lastName("Ogunbanwo")
                .build();

        customer = CustomerProfile.builder()
                .customerProfileId(10L)
                .user(user)
                .build();

        vendor = VendorProfile.builder()
                .id(20L)
                .user(vendorUser)
                .restaurantName("Jollof Palace")
                .build();

        product = Product.builder()
                .productId(30L)
                .publicProductId(PRODUCT_PUBLIC_ID)
                .name("Jollof Rice")
                .price(BigDecimal.valueOf(15.99))
                .available(true)
                .vendor(vendor)
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    }

    private FavoriteRequestDto vendorRequest() {
        FavoriteRequestDto dto = new FavoriteRequestDto();
        dto.setFavoriteType(FavoriteType.VENDOR);
        dto.setVendorPublicId(VENDOR_PUBLIC_ID);
        return dto;
    }

    private FavoriteRequestDto productRequest() {
        FavoriteRequestDto dto = new FavoriteRequestDto();
        dto.setFavoriteType(FavoriteType.PRODUCT);
        dto.setProductPublicId(PRODUCT_PUBLIC_ID);
        return dto;
    }

    // ========== addFavorite — VENDOR ==========

    @Nested
    class AddVendorFavorite {

        @Test
        void addsVendorFavorite_whenNotAlreadyFavorited() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(false);

            Favorite saved = Favorite.builder()
                    .favoriteId(100L)
                    .publicFavoriteId("fav-vendor-100")
                    .customer(customer)
                    .favoriteType(FavoriteType.VENDOR)
                    .vendor(vendor)
                    .build();
            when(favoriteRepository.save(any(Favorite.class))).thenReturn(saved);

            FavoriteResponseDto result = favoriteService.addFavorite(USERNAME, vendorRequest());

            assertThat(result.getPublicFavoriteId()).isEqualTo("fav-vendor-100");
            assertThat(result.getFavoriteType()).isEqualTo(FavoriteType.VENDOR);
            assertThat(result.getVendor().getPublicVendorId()).isEqualTo(VENDOR_PUBLIC_ID);
            verify(outboxEventService).vendorFavourited(eq(VENDOR_PUBLIC_ID), anyString());
        }

        @Test
        void throwsDuplicate_whenAlreadyFavorited() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(true);

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, vendorRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already in favorites");

            verify(favoriteRepository, never()).save(any());
            verify(outboxEventService, never()).vendorFavourited(anyString(), anyString());
        }

        @Test
        void throwsDuplicate_whenConcurrentInsertLosesUniqueConstraintRace() {
            // Simulates two simultaneous requests: both pass the existsBy pre-check
            // (false), but the second save() hits the DB's unique constraint first.
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(false);
            when(favoriteRepository.save(any(Favorite.class))).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, vendorRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already in favorites");

            verify(outboxEventService, never()).vendorFavourited(anyString(), anyString());
        }

        @Test
        void throwsNotFound_whenVendorDoesNotExist() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, vendorRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vendor not found");
        }

        @Test
        void suppressesDuplicateVendorNotification_onRapidFavoriteUnfavoriteFavoriteCycle() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.save(any(Favorite.class))).thenReturn(
                    Favorite.builder().favoriteId(1L).publicFavoriteId("fav-vendor-1").customer(customer).favoriteType(FavoriteType.VENDOR).vendor(vendor).build());

            // First favorite: not yet favorited -> notification fires.
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(false);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                    .thenReturn(true)
                    .thenReturn(false);
            favoriteService.addFavorite(USERNAME, vendorRequest());

            // Customer unfavorites then immediately re-favorites the same vendor
            // (existsBy flips back to false once removed) -> the second add should
            // NOT fire a second notification, since it's within the cooldown window.
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(false);
            favoriteService.addFavorite(USERNAME, vendorRequest());

            verify(outboxEventService, times(1)).vendorFavourited(eq(VENDOR_PUBLIC_ID), anyString());
        }
    }

    // ========== addFavorite — PRODUCT ==========

    @Nested
    class AddProductFavorite {

        @Test
        void addsProductFavorite_whenNotAlreadyFavorited() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.existsByCustomerAndProduct(customer, product)).thenReturn(false);

            Favorite saved = Favorite.builder()
                    .favoriteId(101L)
                    .publicFavoriteId("fav-product-101")
                    .customer(customer)
                    .favoriteType(FavoriteType.PRODUCT)
                    .product(product)
                    .build();
            when(favoriteRepository.save(any(Favorite.class))).thenReturn(saved);

            FavoriteResponseDto result = favoriteService.addFavorite(USERNAME, productRequest());

            assertThat(result.getPublicFavoriteId()).isEqualTo("fav-product-101");
            assertThat(result.getFavoriteType()).isEqualTo(FavoriteType.PRODUCT);
            assertThat(result.getProduct().getPublicProductId()).isEqualTo(PRODUCT_PUBLIC_ID);
            // Product favorites don't currently trigger a vendor notification.
            verifyNoInteractions(outboxEventService);
        }

        @Test
        void throwsDuplicate_whenAlreadyFavorited() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.existsByCustomerAndProduct(customer, product)).thenReturn(true);

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, productRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already in favorites");

            verify(favoriteRepository, never()).save(any());
        }

        @Test
        void throwsDuplicate_whenConcurrentInsertLosesUniqueConstraintRace() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.existsByCustomerAndProduct(customer, product)).thenReturn(false);
            when(favoriteRepository.save(any(Favorite.class))).thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, productRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already in favorites");
        }

        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, productRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found");
        }
    }

    // ========== addFavorite — user / customer resolution ==========

    @Nested
    class UserResolution {

        @Test
        void throwsNotFound_whenUserCannotBeResolvedByUsernameOrEmail() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, vendorRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");

            // Regression guard for the findByEmail bug: the service must never
            // fall back to a plain findByEmail lookup for auth resolution.
            verify(userRepository, never()).findByEmail(anyString());
        }

        @Test
        void resolvesUser_byEmailIdentifierToo() {
            // findByUsernameOrEmail is a single query matching either column —
            // confirm the service passes the raw principal name through untouched
            // regardless of whether it's actually a username or an email.
            String emailIdentifier = "ade@afrochow.com";
            when(userRepository.findByUsernameOrEmail(emailIdentifier)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(false);
            when(favoriteRepository.save(any(Favorite.class))).thenReturn(
                    Favorite.builder().favoriteId(1L).publicFavoriteId("fav-vendor-1").customer(customer).favoriteType(FavoriteType.VENDOR).vendor(vendor).build());

            favoriteService.addFavorite(emailIdentifier, vendorRequest());

            verify(userRepository).findByUsernameOrEmail(emailIdentifier);
        }

        @Test
        void throwsNotFound_whenCustomerProfileMissing() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, vendorRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer profile not found");
        }
    }

    // ========== addFavorite — request validation ==========

    @Nested
    class RequestValidation {

        @Test
        void rejectsVendorType_withMissingVendorId() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));

            FavoriteRequestDto dto = new FavoriteRequestDto();
            dto.setFavoriteType(FavoriteType.VENDOR);

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vendor ID is required");
        }

        @Test
        void rejectsVendorType_withProductIdAlsoSet() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));

            FavoriteRequestDto dto = new FavoriteRequestDto();
            dto.setFavoriteType(FavoriteType.VENDOR);
            dto.setVendorPublicId(VENDOR_PUBLIC_ID);
            dto.setProductPublicId(PRODUCT_PUBLIC_ID);

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be null");
        }

        @Test
        void rejectsProductType_withMissingProductId() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));

            FavoriteRequestDto dto = new FavoriteRequestDto();
            dto.setFavoriteType(FavoriteType.PRODUCT);

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Product ID is required");
        }

        @Test
        void rejectsProductType_withVendorIdAlsoSet() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));

            FavoriteRequestDto dto = new FavoriteRequestDto();
            dto.setFavoriteType(FavoriteType.PRODUCT);
            dto.setProductPublicId(PRODUCT_PUBLIC_ID);
            dto.setVendorPublicId(VENDOR_PUBLIC_ID);

            assertThatThrownBy(() -> favoriteService.addFavorite(USERNAME, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be null");
        }
    }

    // ========== removeFavorite ==========

    @Nested
    class RemoveFavorite {

        @Test
        void removesVendorFavorite_whenItExists() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(true);

            favoriteService.removeFavorite(USERNAME, vendorRequest());

            verify(favoriteRepository).deleteByCustomerAndVendor(customer, vendor);
        }

        @Test
        void throwsNotFound_whenVendorFavoriteDoesNotExist() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(false);

            assertThatThrownBy(() -> favoriteService.removeFavorite(USERNAME, vendorRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not in favorites");

            verify(favoriteRepository, never()).deleteByCustomerAndVendor(any(), any());
        }

        @Test
        void removesProductFavorite_whenItExists() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.existsByCustomerAndProduct(customer, product)).thenReturn(true);

            favoriteService.removeFavorite(USERNAME, productRequest());

            verify(favoriteRepository).deleteByCustomerAndProduct(customer, product);
        }

        @Test
        void throwsNotFound_whenProductFavoriteDoesNotExist() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.existsByCustomerAndProduct(customer, product)).thenReturn(false);

            assertThatThrownBy(() -> favoriteService.removeFavorite(USERNAME, productRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not in favorites");

            verify(favoriteRepository, never()).deleteByCustomerAndProduct(any(), any());
        }
    }

    // ========== read / status / count endpoints ==========

    @Nested
    class ReadEndpoints {

        @Test
        void getAllFavorites_mapsEntitiesToDtos() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));

            Favorite vendorFav = Favorite.builder().favoriteId(1L).publicFavoriteId("fav-vendor-1").customer(customer).favoriteType(FavoriteType.VENDOR).vendor(vendor).build();
            Favorite productFav = Favorite.builder().favoriteId(2L).publicFavoriteId("fav-product-2").customer(customer).favoriteType(FavoriteType.PRODUCT).product(product).build();
            PageRequest pageable = PageRequest.of(0, 20);
            when(favoriteRepository.findByCustomerOrderByCreatedAtDesc(customer, pageable))
                    .thenReturn(new PageImpl<>(List.of(vendorFav, productFav), pageable, 2));

            Page<FavoriteResponseDto> result = favoriteService.getAllFavorites(USERNAME, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getVendor().getRestaurantName()).isEqualTo("Jollof Palace");
            assertThat(result.getContent().get(1).getProduct().getProductName()).isEqualTo("Jollof Rice");
        }

        @Test
        void getFavoritesByType_filtersByType() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));

            Favorite productFav = Favorite.builder().favoriteId(2L).publicFavoriteId("fav-product-2").customer(customer).favoriteType(FavoriteType.PRODUCT).product(product).build();
            PageRequest pageable = PageRequest.of(0, 20);
            when(favoriteRepository.findByCustomerAndFavoriteTypeOrderByCreatedAtDesc(customer, FavoriteType.PRODUCT, pageable))
                    .thenReturn(new PageImpl<>(List.of(productFav), pageable, 1));

            Page<FavoriteResponseDto> result = favoriteService.getFavoritesByType(USERNAME, FavoriteType.PRODUCT, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getFavoriteType()).isEqualTo(FavoriteType.PRODUCT);
        }

        @Test
        void isVendorFavorited_returnsTrueWhenExists() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.existsByCustomerAndVendor(customer, vendor)).thenReturn(true);

            assertThat(favoriteService.isVendorFavorited(USERNAME, VENDOR_PUBLIC_ID)).isTrue();
        }

        @Test
        void isProductFavorited_returnsFalseWhenAbsent() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.existsByCustomerAndProduct(customer, product)).thenReturn(false);

            assertThat(favoriteService.isProductFavorited(USERNAME, PRODUCT_PUBLIC_ID)).isFalse();
        }

        @Test
        void getVendorFavoriteCount_delegatesToRepository() {
            when(vendorProfileRepository.findByUser_PublicUserId(VENDOR_PUBLIC_ID)).thenReturn(Optional.of(vendor));
            when(favoriteRepository.countByVendor(vendor)).thenReturn(7L);

            assertThat(favoriteService.getVendorFavoriteCount(VENDOR_PUBLIC_ID)).isEqualTo(7L);
        }

        @Test
        void getProductFavoriteCount_delegatesToRepository() {
            when(productRepository.findByPublicProductId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
            when(favoriteRepository.countByProduct(product)).thenReturn(3L);

            assertThat(favoriteService.getProductFavoriteCount(PRODUCT_PUBLIC_ID)).isEqualTo(3L);
        }

        @Test
        void getCustomerFavoriteCount_delegatesToRepository() {
            when(userRepository.findByUsernameOrEmail(USERNAME)).thenReturn(Optional.of(user));
            when(customerProfileRepository.findByUser(user)).thenReturn(Optional.of(customer));
            when(favoriteRepository.countByCustomer(customer)).thenReturn(5L);

            assertThat(favoriteService.getCustomerFavoriteCount(USERNAME)).isEqualTo(5L);
        }
    }
}
