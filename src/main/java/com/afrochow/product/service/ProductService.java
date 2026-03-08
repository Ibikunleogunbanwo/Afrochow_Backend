package com.afrochow.product.service;

import com.afrochow.product.dto.ProductRequestDto;
import com.afrochow.product.dto.ProductUpdateRequestDto;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.dto.ProductSummaryResponseDto;
import com.afrochow.category.model.Category;
import com.afrochow.product.model.Product;
import com.afrochow.image.ImageUploadService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.category.repository.CategoryRepository;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Service for managing products
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final CategoryRepository categoryRepository;
    private final ImageUploadService imageUploadService;
    private final UserRepository userRepository;

    public ProductService(
            ProductRepository productRepository,
            VendorProfileRepository vendorProfileRepository,
            CategoryRepository categoryRepository,
            ImageUploadService imageUploadService,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.categoryRepository = categoryRepository;
        this.imageUploadService = imageUploadService;
        this.userRepository = userRepository;
    }

    // ========== PUBLIC METHODS (no authentication required) ==========

    /**
     * Get all available products
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getAllAvailableProducts() {
        List<Product> products = productRepository.findByAvailable(true);
        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get product by public ID
     */
    @Transactional(readOnly = true)
    public ProductResponseDto getProductByPublicId(String publicProductId) {
        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        return toResponseDto(product);
    }

    /**
     * Get products by vendor public ID
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getProductsByVendor(String publicVendorId, Boolean availableOnly) {
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(publicVendorId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        List<Product> products = availableOnly
                ? productRepository.findByVendorAndAvailable(vendor, true)
                : productRepository.findByVendor(vendor);

        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get products by category
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getProductsByCategory(Long categoryId, Boolean availableOnly) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        List<Product> products = availableOnly
                ? productRepository.findByCategoryAndAvailable(category, true)
                : productRepository.findByCategory(category);

        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Search products by name or description
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> searchProducts(String query) {
        List<Product> products = productRepository
                .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Filter products by price range
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        List<Product> products = productRepository.findByPriceBetweenAndAvailable(minPrice, maxPrice, true);
        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vegetarian products
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getVegetarianProducts() {
        List<Product> products = productRepository.findByIsVegetarianAndAvailable(true, true);
        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vegan products
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getVeganProducts() {
        List<Product> products = productRepository.findByIsVeganAndAvailable(true, true);
        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get gluten-free products
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponseDto> getGlutenFreeProducts() {
        List<Product> products = productRepository.findByIsGlutenFreeAndAvailable(true, true);
        return products.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    // ========== VENDOR METHODS (requires VENDOR role) ==========

    /**
     * Create a new product (vendor only)
     */

    @Transactional
    public ProductResponseDto createProduct(String username, ProductRequestDto request) {
        // Get vendor profile
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        // Check if vendor is verified
        if (!vendor.getIsVerified()) {
            throw new IllegalStateException("Only verified vendors can create products");
        }

        // Map to entity
        Product product = toEntity(request);
        // Don't set vendor here yet - let vendor.addProduct() handle it

        // Set category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found"));
            product.setCategory(category);
        }

        // Use vendor's addProduct method to maintain bidirectional relationship
        vendor.addProduct(product);

        // This will cascade and save the product
        vendorProfileRepository.save(vendor);

        // Now we can get the saved product from the vendor's collection
        Product savedProduct = vendor.getProducts().stream()
                .filter(p -> p.getName().equals(product.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to save product"));

        return toResponseDto(savedProduct);
    }

    /**
     * Get all products for a vendor (including unavailable ones)
     */
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getVendorProducts(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        VendorProfile vendor = vendorProfileRepository.findByUser_UserId(user.get().getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        List<Product> products = productRepository.findByVendor(vendor);
        return products.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vendor product by public ID (ownership check)
     */
    @Transactional(readOnly = true)
    public ProductResponseDto getVendorProduct(Long vendorUserId, String publicProductId) {
        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // Verify ownership
        if (!product.getVendor().getUser().getUserId().equals(vendorUserId)) {
            throw new IllegalStateException("You can only view your own products");
        }

        return toResponseDto(product);
    }

    /**
     * Update a product (vendor only, ownership check)
     */
    @Transactional
    public ProductResponseDto updateProduct(String username,
                                            String publicProductId,
                                            ProductUpdateRequestDto request) {

        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        /* ---- ownership gate ---- */
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw new AccessDeniedException("You can only update your own products");
        }
        /* ------------------------ */

        // merge non-null fields
        updateEntityFromDto(request, product);

        // optional category switch
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found"));
            product.setCategory(category);
        }

        return toResponseDto(productRepository.save(product));
    }

    /**
     * Delete a product (vendor only, ownership check)
     */

    public void deleteProduct(String username, String publicProductId) {
        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // Verify ownership
        VendorProfile vendor = vendorProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));

        // Use vendor's removeProduct method which handles bidirectional cleanup
        boolean removed = vendor.removeProduct(product);

        if (removed) {
            // If successfully removed from vendor's collection, delete from repository
            productRepository.delete(product);
        } else {
            // Product might be deactivated instead of removed (due to existing orders)
            // Save the deactivated product
            productRepository.save(product);
        }
    }

    /**
     * Toggle product availability (vendor only)
     */
    @Transactional(readOnly = true)
    public ProductResponseDto toggleProductAvailability(Long vendorUserId, String publicProductId) {
        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // Verify ownership
        if (!product.getVendor().getUser().getUserId().equals(vendorUserId)) {
            throw new IllegalStateException("You can only modify your own products");
        }

        // Toggle availability
        product.setAvailable(!product.getAvailable());
        Product updatedProduct = productRepository.save(product);

        return toResponseDto(updatedProduct);
    }

    /**
     * Upload product image (vendor only)
     *
     * @param vendorUserId User ID of the vendor
     * @param publicProductId Public product ID
     * @param file Image file to upload
     * @return Updated product
     * @throws IOException if upload fails
     */
    @Transactional(readOnly = true)
    public ProductResponseDto uploadProductImage(Long vendorUserId, String publicProductId, MultipartFile file)
            throws IOException {
        Product product = productRepository.findByPublicProductId(publicProductId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // Verify ownership
        if (!product.getVendor().getUser().getUserId().equals(vendorUserId)) {
            throw new IllegalStateException("You can only modify your own products");
        }

        // Delete old image if exists
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            imageUploadService.deleteImage(product.getImageUrl());
        }

        // Upload new image
        String imagePath = imageUploadService.uploadImageForRegistrationAndGetUrl(file, "products");
        product.setImageUrl(imagePath);

        // Save and return
        Product updatedProduct = productRepository.save(product);
        return toResponseDto(updatedProduct);
    }

    // ========== STATISTICS ==========

    /**
     * Count products for a vendor
     */
    public Long countVendorProducts(Long vendorUserId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_UserId(vendorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));
        return productRepository.countByVendor(vendor);
    }

    /**
     * Count available products for a vendor
     */
    public Long countAvailableVendorProducts(Long vendorUserId) {
        VendorProfile vendor = vendorProfileRepository.findByUser_UserId(vendorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));
        return productRepository.countByVendorAndAvailable(vendor, true);
    }

    // ========== MAPPING METHODS ==========

    private ProductResponseDto toResponseDto(Product product) {
        return ProductResponseDto.builder()
                .publicProductId(product.getPublicProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .available(product.getAvailable())
                .preparationTimeMinutes(product.getPreparationTimeMinutes())
                .calories(product.getCalories())
                .isVegetarian(product.getIsVegetarian())
                .isVegan(product.getIsVegan())
                .isGlutenFree(product.getIsGlutenFree())
                .isSpicy(product.getIsSpicy())
                .vendorPublicId(product.getVendor() != null ? product.getVendor().getPublicVendorId() : null)
                .restaurantName(product.getVendor() != null ? product.getVendor().getRestaurantName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .totalOrders(product.getTotalOrders())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private ProductSummaryResponseDto toSummaryResponseDto(Product product) {
        return ProductSummaryResponseDto.builder()
                .publicProductId(product.getPublicProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .available(product.getAvailable())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .build();
    }

    private Product toEntity(ProductRequestDto dto) {
        return Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .imageUrl(dto.getImageUrl())
                .available(dto.getAvailable())
                .preparationTimeMinutes(dto.getPreparationTimeMinutes())
                .calories(dto.getCalories())
                .isVegetarian(dto.getIsVegetarian())
                .isVegan(dto.getIsVegan())
                .isGlutenFree(dto.getIsGlutenFree())
                .isSpicy(dto.getIsSpicy())
                .build();
    }

    private void updateEntityFromDto(ProductUpdateRequestDto dto, Product product) {
        if (dto.getName() != null) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getImageUrl() != null) product.setImageUrl(dto.getImageUrl());
        if (dto.getAvailable() != null) product.setAvailable(dto.getAvailable());
        if (dto.getPreparationTimeMinutes() != null) product.setPreparationTimeMinutes(dto.getPreparationTimeMinutes());
        if (dto.getCalories() != null) product.setCalories(dto.getCalories());
        if (dto.getIsVegetarian() != null) product.setIsVegetarian(dto.getIsVegetarian());
        if (dto.getIsVegan() != null) product.setIsVegan(dto.getIsVegan());
        if (dto.getIsGlutenFree() != null) product.setIsGlutenFree(dto.getIsGlutenFree());
        if (dto.getIsSpicy() != null) product.setIsSpicy(dto.getIsSpicy());
    }
}
