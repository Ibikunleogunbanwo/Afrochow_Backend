package com.afrochow.product.service;

import com.afrochow.category.model.Category;
import com.afrochow.category.repository.CategoryRepository;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.favorite.repository.FavoriteRepository;
import com.afrochow.image.service.ImageUploadService;
import com.afrochow.image.service.ImageCleanupService;
import com.afrochow.product.dto.ProductRequestDto;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.dto.ProductSummaryResponseDto;
import com.afrochow.product.dto.ProductUpdateRequestDto;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ImageUploadService imageUploadService;
    @Mock private ImageCleanupService imageCleanupService;
    @Mock private UserRepository userRepository;
    @Mock private FavoriteRepository favoriteRepository;

    @InjectMocks private ProductService productService;

    private User vendorUser;
    private VendorProfile vendor;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        vendorUser = User.builder().userId(10L).username("jollofhouse").build();
        vendor = VendorProfile.builder().id(5L).user(vendorUser)
                .restaurantName("Jollof House").vendorStatus(VendorStatus.VERIFIED).build();
        category = Category.builder().categoryId(2L).name("African Kitchen").build();
        product = Product.builder()
                .productId(1L).publicProductId("PROD-V5-abc12345")
                .name("Jollof Rice").description("Spicy rice").price(new BigDecimal("15.00"))
                .available(true).adminVisible(true).preparationTimeMinutes(30)
                .scheduleType(ScheduleType.SAME_DAY)
                .vendor(vendor).category(category)
                .build();
    }

    // ========== createProduct ==========

    @Test
    void createProduct_verifiedVendor_savesAndReturnsDto() {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Jollof Rice").price(new BigDecimal("15.00"))
                .preparationTimeMinutes(30).categoryId(2L).build();
        when(vendorProfileRepository.findByUser_Username("jollofhouse")).thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDto result = productService.createProduct("jollofhouse", request);

        assertThat(result.getName()).isEqualTo("Jollof Rice");
        assertThat(result.getCategoryId()).isEqualTo(2L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_provisionalVendor_isAllowed() {
        vendor.setVendorStatus(VendorStatus.PROVISIONAL);
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Jollof Rice").price(new BigDecimal("15.00"))
                .preparationTimeMinutes(30).build();
        when(vendorProfileRepository.findByUser_Username("jollofhouse")).thenReturn(Optional.of(vendor));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDto result = productService.createProduct("jollofhouse", request);

        assertThat(result.getName()).isEqualTo("Jollof Rice");
    }

    @Test
    void createProduct_unapprovedVendorStatus_throwsIllegalState() {
        vendor.setVendorStatus(VendorStatus.PENDING_PROFILE);
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Jollof Rice").price(new BigDecimal("15.00"))
                .preparationTimeMinutes(30).build();
        when(vendorProfileRepository.findByUser_Username("jollofhouse")).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> productService.createProduct("jollofhouse", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_PROFILE");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_categoryNotFound_throwsEntityNotFound() {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Jollof Rice").price(new BigDecimal("15.00"))
                .preparationTimeMinutes(30).categoryId(99L).build();
        when(vendorProfileRepository.findByUser_Username("jollofhouse")).thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct("jollofhouse", request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== getVendorProduct ==========

    @Test
    void getVendorProduct_ownedByCaller_returnsDto() {
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        ProductResponseDto result = productService.getVendorProduct(10L, "PROD-V5-abc12345");

        assertThat(result.getPublicProductId()).isEqualTo("PROD-V5-abc12345");
    }

    @Test
    void getVendorProduct_notOwnedByCaller_throwsIllegalState() {
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.getVendorProduct(999L, "PROD-V5-abc12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own products");
    }

    // ========== updateProduct ==========

    @Test
    void updateProduct_ownedByCaller_mergesAndSaves() {
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder().name("Updated Rice").build();
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));
        when(vendorProfileRepository.findByUser_Username("jollofhouse")).thenReturn(Optional.of(vendor));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDto result = productService.updateProduct("jollofhouse", "PROD-V5-abc12345", request);

        assertThat(result.getName()).isEqualTo("Updated Rice");
    }

    @Test
    void updateProduct_categorySwitch_updatesCategory() {
        Category newCategory = Category.builder().categoryId(3L).name("Soups").build();
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder().categoryId(3L).build();
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));
        when(vendorProfileRepository.findByUser_Username("jollofhouse")).thenReturn(Optional.of(vendor));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(newCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDto result = productService.updateProduct("jollofhouse", "PROD-V5-abc12345", request);

        assertThat(result.getCategoryId()).isEqualTo(3L);
    }

    @Test
    void updateProduct_notOwnedByCaller_throwsAccessDenied() {
        // NOTE: updateProduct uses a different exception type (AccessDeniedException)
        // than the other ownership checks in this class (IllegalStateException /
        // IllegalArgumentException) — verified against the actual production code.
        ProductUpdateRequestDto request = ProductUpdateRequestDto.builder().name("Hacked").build();
        User otherUser = User.builder().userId(20L).username("otherguy").build();
        VendorProfile otherVendor = VendorProfile.builder().id(6L).user(otherUser)
                .vendorStatus(VendorStatus.VERIFIED).build();
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));
        when(vendorProfileRepository.findByUser_Username("otherguy")).thenReturn(Optional.of(otherVendor));

        assertThatThrownBy(() -> productService.updateProduct("otherguy", "PROD-V5-abc12345", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("your own products");
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_productNotFound_throwsEntityNotFound() {
        when(productRepository.findByPublicProductId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct("jollofhouse", "missing",
                ProductUpdateRequestDto.builder().build()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========== deleteProduct ==========

    @Test
    void deleteProduct_ownedByCaller_clearsFavoritesBeforeDeleting() {
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        productService.deleteProduct("jollofhouse", "PROD-V5-abc12345");

        InOrder inOrder = inOrder(favoriteRepository, productRepository);
        inOrder.verify(favoriteRepository).deleteAllByProduct(product);
        inOrder.verify(productRepository).delete(product);
    }

    @Test
    void deleteProduct_notOwnedByCaller_throwsIllegalArgument() {
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct("someoneelse", "PROD-V5-abc12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not own");
        verify(productRepository, never()).delete(any());
        verify(favoriteRepository, never()).deleteAllByProduct(any());
    }

    // ========== toggleProductAvailability ==========

    @Test
    void toggleProductAvailability_flipsAvailableFlagUsingLockedFetch() {
        when(productRepository.findByPublicProductIdWithLock("PROD-V5-abc12345")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDto result = productService.toggleProductAvailability(10L, "PROD-V5-abc12345");

        assertThat(result.getAvailable()).isFalse();
        verify(productRepository).findByPublicProductIdWithLock("PROD-V5-abc12345");
        verify(productRepository, never()).findByPublicProductId(any());
    }

    @Test
    void toggleProductAvailability_notOwnedByCaller_throwsIllegalState() {
        when(productRepository.findByPublicProductIdWithLock("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.toggleProductAvailability(999L, "PROD-V5-abc12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own products");
        verify(productRepository, never()).save(any());
    }

    // ========== uploadProductImage ==========

    @Test
    void uploadProductImage_replacesExistingImage_enqueuesOldForCleanup() throws Exception {
        product.setImageUrl("https://cdn.example.com/old-image.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(eq(file), eq("products")))
                .thenReturn("https://cdn.example.com/new-image.jpg");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDto result = productService.uploadProductImage(10L, "PROD-V5-abc12345", file);

        assertThat(result.getImageUrl()).isEqualTo("https://cdn.example.com/new-image.jpg");
        verify(imageCleanupService).enqueue("https://cdn.example.com/old-image.jpg", "product-image-replaced");
    }

    @Test
    void uploadProductImage_noExistingImage_skipsCleanupEnqueue() throws Exception {
        product.setImageUrl(null);
        MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));
        when(imageUploadService.uploadImageForRegistrationAndGetUrl(eq(file), eq("products")))
                .thenReturn("https://cdn.example.com/new-image.jpg");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.uploadProductImage(10L, "PROD-V5-abc12345", file);

        verify(imageCleanupService, never()).enqueue(any(), any());
    }

    @Test
    void uploadProductImage_notOwnedByCaller_throwsIllegalState() {
        MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", "data".getBytes());
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.uploadProductImage(999L, "PROD-V5-abc12345", file))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========== public listing methods ==========

    @Test
    void getProductByPublicId_found_returnsDto() {
        when(productRepository.findByPublicProductId("PROD-V5-abc12345")).thenReturn(Optional.of(product));

        ProductResponseDto result = productService.getProductByPublicId("PROD-V5-abc12345");

        assertThat(result.getName()).isEqualTo("Jollof Rice");
    }

    @Test
    void getProductByPublicId_notFound_throwsEntityNotFound() {
        when(productRepository.findByPublicProductId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductByPublicId("missing"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProductsByVendor_availableOnlyTrue_usesAdminVisibleFilteredQuery() {
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByVendorAndAvailableTrueAndAdminVisibleTrue(vendor))
                .thenReturn(List.of(product));

        List<ProductSummaryResponseDto> result = productService.getProductsByVendor("VEN123", true);

        assertThat(result).hasSize(1);
        verify(productRepository, never()).findByVendorAndAdminVisibleTrue(any());
    }

    @Test
    void getProductsByVendor_availableOnlyFalse_returnsAllAdminVisibleProducts() {
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByVendorAndAdminVisibleTrue(vendor)).thenReturn(List.of(product));

        List<ProductSummaryResponseDto> result = productService.getProductsByVendor("VEN123", false);

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductsByVendor_vendorNotFound_throwsEntityNotFound() {
        when(vendorProfileRepository.findByUser_PublicUserId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductsByVendor("missing", true))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getProductsByCategory_availableOnlyTrue_usesAdminVisibleFilteredQuery() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.findByCategoryAndAvailableTrueAndAdminVisibleTrue(category))
                .thenReturn(List.of(product));

        List<ProductSummaryResponseDto> result = productService.getProductsByCategory(2L, true);

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductsByCategory_categoryNotFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductsByCategory(99L, true))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getVendorProducts_returnsAllProductsRegardlessOfAvailability() {
        when(userRepository.findByUsername("jollofhouse")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(10L)).thenReturn(Optional.of(vendor));
        when(productRepository.findByVendor(vendor)).thenReturn(List.of(product));

        List<ProductResponseDto> result = productService.getVendorProducts("jollofhouse");

        assertThat(result).hasSize(1);
    }

    // ========== stats ==========

    @Test
    void countVendorProducts_delegatesToRepository() {
        when(vendorProfileRepository.findByUser_UserId(10L)).thenReturn(Optional.of(vendor));
        when(productRepository.countByVendor(vendor)).thenReturn(4L);

        assertThat(productService.countVendorProducts(10L)).isEqualTo(4L);
    }

    @Test
    void countAvailableVendorProducts_delegatesToRepository() {
        when(vendorProfileRepository.findByUser_UserId(10L)).thenReturn(Optional.of(vendor));
        when(productRepository.countByVendorAndAvailable(vendor, true)).thenReturn(3L);

        assertThat(productService.countAvailableVendorProducts(10L)).isEqualTo(3L);
    }
}
