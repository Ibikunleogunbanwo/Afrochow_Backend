package com.afrochow.seeder;

import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.category.model.Category;
import com.afrochow.category.repository.CategoryRepository;
import com.afrochow.common.enums.Role;
import com.afrochow.common.enums.Province;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.review.model.Review;
import com.afrochow.review.repository.ReviewRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Complete Final Database Seeder - Fixed Version
 * Seeds categories, vendors, products, and reviews
 */
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class CompleteFinalSeeder implements CommandLineRunner {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryRepository categoryRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    // Configuration constants
    private static final String DEFAULT_PASSWORD = "Demo@123";
    private static final String DEFAULT_CITY = "Calgary";
    private static final Province DEFAULT_PROVINCE = Province.AB;
    private static final String DEFAULT_COUNTRY = "Canada";

    // Realistic Calgary data
    private static final String[] CALGARY_STREETS = {
        "17 Avenue SW", "Centre Street N", "MacLeod Trail SE", "Crowchild Trail NW",
        "Memorial Drive NE", "Bow Trail SW", "Blackfoot Trail SE", "Deerfoot Trail NE",
        "Edmonton Trail NE", "Barlow Trail SE", "36 Street NE", "52 Street SE"
    };

    private static final String[] AREA_CODES = {"403", "587", "825"};

    // Real food images from Cloudinary
    private static final String[] FOOD_IMAGES = {
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/Amala_jlxqmn.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/vk-bro-al9eh9QkdPA-unsplash_hgb5fp.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919508/nico-smit-9ZJOs9hmuKs-unsplash_n3fwbt.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/omotayo-tajudeen-ME416b6sp2I-unsplash_jxh2qx.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nathan-dumlao-1lAIRAsv3C4-unsplash_gmm3t6.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/victoria-shes-UC0HZdUitWY-unsplash_wa1zr0.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919509/etty-fidele-oJpkjWcScyg-unsplash_b5htn1.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/ASORTED_Food_tg6kzh.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919510/emile-mbunzama-cLpdEA23Z44-unsplash_qzxna1.jpg",
        "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nAIJA_FOOD_n7daze.jpg"
    };

    // Sample business license numbers and tax IDs
    private static final String[] BUSINESS_LICENSES = {
        "BL-CGY-2024-001", "BL-CGY-2024-002", "BL-CGY-2024-003", "BL-CGY-2024-004", "BL-CGY-2024-005",
        "BL-CGY-2024-006", "BL-CGY-2024-007", "BL-CGY-2024-008", "BL-CGY-2024-009", "BL-CGY-2024-010",
        "BL-CGY-2024-011", "BL-CGY-2024-012", "BL-CGY-2024-013", "BL-CGY-2024-014", "BL-CGY-2024-015",
        "BL-CGY-2024-016", "BL-CGY-2024-017", "BL-CGY-2024-018", "BL-CGY-2024-019", "BL-CGY-2024-020"
    };

    private static final String[] TAX_IDS = {
        "987654321RC0001", "987654321RC0002", "987654321RC0003", "987654321RC0004", "987654321RC0005",
        "987654321RC0006", "987654321RC0007", "987654321RC0008", "987654321RC0009", "987654321RC0010",
        "987654321RC0011", "987654321RC0012", "987654321RC0013", "987654321RC0014", "987654321RC0015",
        "987654321RC0016", "987654321RC0017", "987654321RC0018", "987654321RC0019", "987654321RC0020"
    };

    // Configuration classes
    @Data
    @AllArgsConstructor
    private static class CategoryConfiguration {
        String name;
        String description;
        String icon;
        int displayOrder;
        String colors;
        boolean isActive;
    }

    @Data
    @AllArgsConstructor
    private static class VendorConfiguration {
        String name;
        String description;
        String specialty;
        boolean offersDelivery;
        boolean offersPickup;
        double rating;
        String openTime;
        String closeTime;
        boolean openOnSunday;
    }

    // Enhanced category configurations with icons
    private static final Map<String, CategoryConfiguration> CATEGORY_CONFIGS = Map.of(
        "African Kitchen", new CategoryConfiguration(
            "African Kitchen",
            "Authentic African meals including jollof rice, pounded yam, egusi soup, and traditional dishes",
            "🍲", 1, "FF6B35,F7931E", true
        ),
        "Cakes", new CategoryConfiguration(
            "Cakes",
            "Custom celebration cakes, birthday cakes, wedding cakes, and traditional African cakes",
            "🎂", 4, "E91E63,F06292", true
        ),
        "Farm Produce", new CategoryConfiguration(
            "Farm Produce",
            "Fresh African vegetables, yams, plantains, honey beans, and organic produce",
            "🥬", 5, "4CAF50,66BB6A", true
        ),
        "Pastries & Baked Goods", new CategoryConfiguration(
            "Pastries & Baked Goods",
            "Freshly baked African pastries, meat pies, sausage rolls, chin chin, and puff puff",
            "🥐", 3, "FF9800,F57C00", true
        ),
        "African Soups", new CategoryConfiguration(
            "African Soups",
            "Traditional African soups including egusi, ogbono, efo riro, pepper soup, and ukazi",
            "🍜", 2, "795548,8D6E63", true
        ),
        "African Groceries", new CategoryConfiguration(
            "African Groceries",
            "Authentic African ingredients, spices, palm oil, garri, egusi seeds, and specialty items",
            "🛒", 6, "607D8B,78909C", true
        )
    );

    // Enhanced vendor configurations
    private static final Map<String, List<VendorConfiguration>> VENDOR_CONFIGS = Map.of(
        "African Kitchen", List.of(
            new VendorConfiguration("Mama Blessing's Kitchen", "Authentic Nigerian home cooking with traditional recipes passed down through generations", "african", true, true, 4.8, "07:00", "22:00", true),
            new VendorConfiguration("Jollof Palace Calgary", "Premium West African cuisine featuring our signature party jollof and grilled specialties", "west-african", true, true, 4.7, "08:00", "22:00", true),
            new VendorConfiguration("Sister Ayo's Kitchen", "Home-style African cooking with fresh ingredients and authentic flavors daily", "nigerian", true, false, 4.6, "09:00", "21:00", false),
            new VendorConfiguration("Lagos Taste Restaurant", "Bringing the vibrant flavors of Lagos to Calgary with modern presentation", "nigerian", true, true, 4.5, "09:00", "22:00", true),
            new VendorConfiguration("Afro Fusion Kitchen", "Contemporary African cuisine blending traditional flavors with modern techniques", "african", true, true, 4.4, "10:00", "21:00", true)
        ),
        "Cakes", List.of(
            new VendorConfiguration("Sweet Lagos Cakes", "Custom African-inspired cakes for weddings, birthdays, and celebrations", "custom-cakes", true, true, 4.9, "06:00", "20:00", true),
            new VendorConfiguration("African Delights Bakery", "Traditional and modern cakes with African flavors and designs", "specialty-cakes", true, true, 4.8, "07:00", "19:00", true),
            new VendorConfiguration("Divine African Cakes", "Handcrafted celebration cakes with authentic African ingredients", "celebration-cakes", true, true, 4.7, "08:00", "18:00", true)
        ),
        "Farm Produce", List.of(
            new VendorConfiguration("Calgary African Produce", "Fresh African vegetables, yams, plantains, and specialty ingredients", "african-produce", true, true, 4.6, "06:00", "19:00", true),
            new VendorConfiguration("Afro Fresh Calgary", "Organic African vegetables and fruits sourced from local and international farms", "organic-produce", true, true, 4.5, "07:00", "18:00", true),
            new VendorConfiguration("Green Valley African Produce", "Direct farm-to-table African vegetables, herbs, and specialty crops", "farm-produce", true, false, 4.4, "08:00", "17:00", false)
        ),
        "Pastries & Baked Goods", List.of(
            new VendorConfiguration("Golden Crust African Bakery", "Freshly baked African breads, meat pies, and pastries daily", "african-bakery", true, true, 4.7, "05:00", "20:00", true),
            new VendorConfiguration("Naija Bakes Calgary", "Authentic Nigerian pastries, meat pies, and baked goods made fresh", "nigerian-pastries", true, true, 4.6, "06:00", "19:00", true),
            new VendorConfiguration("Heritage Pastries", "Traditional African baked goods with recipes from across the continent", "african-pastries", true, true, 4.5, "07:00", "18:00", true)
        ),
        "African Soups", List.of(
            new VendorConfiguration("Mama Ngozi's Soup Spot", "Traditional Nigerian soups made with authentic ingredients and techniques", "nigerian-soups", true, true, 4.8, "08:00", "21:00", true),
            new VendorConfiguration("Traditional Pot Restaurant", "Rich African soups and stews prepared fresh daily with premium ingredients", "african-soups", true, true, 4.7, "09:00", "22:00", true),
            new VendorConfiguration("Royal Pot Restaurant", "Authentic African soups for special occasions and everyday meals", "specialty-soups", true, false, 4.6, "10:00", "20:00", false)
        ),
        "African Groceries", List.of(
            new VendorConfiguration("African Food Store Calgary", "Complete selection of African spices, seasonings, and cooking ingredients", "african-groceries", true, true, 4.5, "08:00", "20:00", true),
            new VendorConfiguration("Naija Market YYC", "Your neighborhood African grocery store with authentic products", "nigerian-groceries", true, true, 4.4, "09:00", "19:00", true),
            new VendorConfiguration("Afro Grocers", "Premium African food products, spices, and specialty items", "premium-groceries", true, true, 4.3, "10:00", "18:00", true)
        )
    );

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (vendorProfileRepository.count() > 0) {
            log.info("Database already seeded with vendor data, skipping seeder...");
            return;
        }

        log.info("Starting complete final vendor seeder...");

        // Create categories
        createAllCategories();

        // Create vendors
        seedVendorData();

        log.info("Complete final vendor seeder completed successfully!");
        log.info("Summary: {} vendors created", vendorProfileRepository.count());
    }

    private void createAllCategories() {
        for (Map.Entry<String, CategoryConfiguration> entry : CATEGORY_CONFIGS.entrySet()) {
            CategoryConfiguration config = entry.getValue();
            createCategoryWithConfiguration(config);
        }
    }

    private void createCategoryWithConfiguration(CategoryConfiguration config) {
        Category category = Category.builder()
                .name(config.name)
                .description(config.description)
                .iconUrl(config.icon)
                .displayOrder(config.displayOrder)
                .isActive(config.isActive)
                .build();

        categoryRepository.save(category);
        log.info("Created category: {} with icon: {}", config.name, config.icon);
    }

    private void seedVendorData() {
        int vendorIndex = 0;
        for (Map.Entry<String, List<VendorConfiguration>> categoryEntry : VENDOR_CONFIGS.entrySet()) {
            String categoryName = categoryEntry.getKey();
            List<VendorConfiguration> vendors = categoryEntry.getValue();

            for (int i = 0; i < vendors.size(); i++) {
                try {
                    VendorConfiguration config = vendors.get(i);
                    createCompleteVendor(config, categoryName, vendorIndex, i);
                    vendorIndex++;
                    log.info("Created vendor: {} with business license: {}", config.name,
                            BUSINESS_LICENSES[vendorIndex % BUSINESS_LICENSES.length]);
                } catch (Exception e) {
                    log.error("Error creating vendor {}: {}", vendors.get(i).name, e.getMessage(), e);
                }
            }
        }
    }

    private void createCompleteVendor(VendorConfiguration config, String categoryName, int vendorIndex, int categoryIndex) {
        User vendorUser = createVendorUser(config, vendorIndex);
        Category category = getCategoryByName(categoryName);
        VendorProfile vendorProfile = createCompleteVendorProfile(vendorUser, config, category, vendorIndex, categoryIndex);
        createCategorySpecificProducts(vendorProfile, category, vendorIndex);
        createRealisticReviews(vendorProfile, config.rating, vendorIndex);
    }

    private Category getCategoryByName(String categoryName) {
        return categoryRepository.findByName(categoryName)
                .orElseThrow(() -> new RuntimeException("Category not found: " + categoryName));
    }

    private User createVendorUser(VendorConfiguration config, int index) {
        String email = generateBusinessEmail(config.name, index);
        String[] nameParts = config.name.split(" ");
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ?
            String.join(" ", Arrays.copyOfRange(nameParts, 1, nameParts.length)) : "Business";

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .firstName(firstName)
                .lastName(lastName)
                .phone(generateCanadianPhoneNumber(1000 + index)) // Offset by 1000 for vendors
                .role(Role.VENDOR)
                .emailVerified(true)
                .isActive(true)
                .build();

        return userRepository.save(user);
    }

    private String generateBusinessEmail(String businessName, int index) {
        String cleanName = businessName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "");
        String[] domains = {"afrochow.com", "africanfood.ca", "calgary.com"};
        return cleanName + index + "@" + domains[index % domains.length];
    }

    private String generateCanadianPhoneNumber(int index) {
        String areaCode = AREA_CODES[index % AREA_CODES.length];
        String centralOffice = String.format("%03d", 200 + (index % 800));
        String lineNumber = String.format("%04d", 1000 + (index % 9000));
        return "+1" + areaCode + centralOffice + lineNumber;
    }

    private Address createRealisticAddress(int index) {
        String street = CALGARY_STREETS[index % CALGARY_STREETS.length];
        int streetNumber = 1000 + (index * 123);

        Address address = Address.builder()
                .addressLine(streetNumber + " " + street)
                .city(DEFAULT_CITY)
                .province(DEFAULT_PROVINCE)
                .country(DEFAULT_COUNTRY)
                .postalCode(generateCalgaryPostalCode(index))
                .build();

        return addressRepository.save(address);
    }

    private String generateCalgaryPostalCode(int index) {
        String[] forwardSortationAreas = {"T1Y", "T2A", "T2B", "T2C", "T2E", "T2G", "T2H", "T2J", "T2K", "T2L"};
        String fsa = forwardSortationAreas[index % forwardSortationAreas.length];
        return fsa + " " + (1 + (index % 9)) + (index % 10) + (index % 9);
    }

    private VendorProfile createCompleteVendorProfile(User vendorUser, VendorConfiguration config,
                                              Category category, int vendorIndex, int categoryIndex) {
        Address address = createRealisticAddress(vendorIndex);

        // Generate business documents
        String businessLicenseUrl = generateBusinessLicenseUrl(config.name, vendorIndex);
        String bannerUrl = generateBannerUrl(config.specialty, vendorIndex);
        String taxId = TAX_IDS[vendorIndex % TAX_IDS.length];

        // Create enhanced operating hours
        Map<String, VendorProfile.DayHours> operatingHours = createEnhancedOperatingHours(
            config.openTime, config.closeTime, config.openOnSunday);

        VendorProfile profile = VendorProfile.builder()
                .user(vendorUser)
                .restaurantName(config.name)
                .description(config.description)
                .cuisineType(category.getName())
                .logoUrl(generateVendorLogoUrl(config.name))
                .bannerUrl(bannerUrl)
                .businessLicenseUrl(businessLicenseUrl)
                .taxId(taxId)
                .address(address)
                .isVerified(true)
                .isActive(true)
                .verifiedAt(LocalDateTime.now().minusDays(30))
                .offersDelivery(config.offersDelivery)
                .offersPickup(config.offersPickup)
                .deliveryFee(BigDecimal.valueOf(2.99 + (vendorIndex * 0.5)))
                .minimumOrderAmount(BigDecimal.valueOf(15.0))
                .estimatedDeliveryMinutes(25 + (vendorIndex * 5))
                .maxDeliveryDistanceKm(BigDecimal.valueOf(15.0))
                .preparationTime(20 + (vendorIndex * 5))
                .totalOrdersCompleted(50 + (vendorIndex * 20))
                .totalRevenue(BigDecimal.valueOf(5000 + (vendorIndex * 1000)))
                .build();

        // Set operating hours
        profile.setOperatingHours(operatingHours);

        return vendorProfileRepository.save(profile);
    }

    private String generateVendorLogoUrl(String vendorName) {
        String cleanName = vendorName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return "https://ui-avatars.com/api/?name=" + cleanName + "&size=200&background=random";
    }

    private String generateBusinessLicenseUrl(String vendorName, int index) {
        return "https://storage.cloud.example.com/business-licenses/" +
               vendorName.toLowerCase().replace(" ", "-") + "-" + index + ".pdf";
    }

    private String generateBannerUrl(String specialty, int index) {
        return "https://picsum.photos/seed/" + specialty + index + "/1200/400";
    }

    private Map<String, VendorProfile.DayHours> createEnhancedOperatingHours(
            String openTime, String closeTime, boolean openOnSunday) {
        Map<String, VendorProfile.DayHours> hours = new HashMap<>();
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

        for (String day : days) {
            VendorProfile.DayHours dayHours = new VendorProfile.DayHours();
            if (day.equals("sunday")) {
                dayHours.setIsOpen(openOnSunday);
                dayHours.setOpenTime(openOnSunday ? openTime : null);
                dayHours.setCloseTime(openOnSunday ? closeTime : null);
            } else {
                dayHours.setIsOpen(true);
                dayHours.setOpenTime(openTime);
                dayHours.setCloseTime(closeTime);
            }
            hours.put(day, dayHours);
        }

        return hours;
    }

    private void createCategorySpecificProducts(VendorProfile vendor, Category category, int vendorIndex) {
        Map<String, List<String[]>> productsByCategory = new HashMap<>();

        // African Kitchen products
        productsByCategory.put("African Kitchen", List.of(
            new String[]{"Jollof Rice", "Delicious party jollof rice with chicken", "25.99"},
            new String[]{"Egusi Soup with Fufu", "Traditional egusi soup with pounded yam", "28.99"},
            new String[]{"Pounded Yam & Egusi", "Smooth pounded yam with rich egusi soup", "26.99"},
            new String[]{"Suya Platter", "Spicy grilled beef skewers with onions and peppers", "22.99"},
            new String[]{"Fried Rice", "Nigerian-style fried rice with mixed vegetables", "23.99"}
        ));

        // Cakes products
        productsByCategory.put("Cakes", List.of(
            new String[]{"Chocolate Celebration Cake", "Rich chocolate cake for special occasions", "45.99"},
            new String[]{"Vanilla Wedding Cake", "Elegant vanilla cake perfect for weddings", "75.99"},
            new String[]{"Red Velvet Cake", "Moist red velvet with cream cheese frosting", "42.99"},
            new String[]{"Fruit Cake", "Traditional African fruit cake", "38.99"}
        ));

        // Farm Produce products
        productsByCategory.put("Farm Produce", List.of(
            new String[]{"Fresh Yam", "Premium quality African yam (per lb)", "4.99"},
            new String[]{"Plantains (6 pcs)", "Green or ripe plantains", "6.99"},
            new String[]{"Cassava (per lb)", "Fresh cassava root", "3.99"},
            new String[]{"African Spinach", "Fresh green vegetables", "5.99"}
        ));

        // Pastries products
        productsByCategory.put("Pastries & Baked Goods", List.of(
            new String[]{"Meat Pies (6 pcs)", "Flaky pastry filled with seasoned meat", "12.99"},
            new String[]{"Chin Chin", "Crunchy fried snack (1 lb)", "8.99"},
            new String[]{"Puff Puff (12 pcs)", "Sweet fried dough balls", "9.99"},
            new String[]{"Sausage Rolls (6 pcs)", "Puff pastry with savory sausage", "11.99"}
        ));

        // Soups products
        productsByCategory.put("African Soups", List.of(
            new String[]{"Egusi Soup (32 oz)", "Rich melon seed soup with assorted meat", "32.99"},
            new String[]{"Ogbono Soup (32 oz)", "Draw soup with African mango seeds", "30.99"},
            new String[]{"Pepper Soup (24 oz)", "Spicy goat meat pepper soup", "28.99"},
            new String[]{"Efo Riro (32 oz)", "Nigerian spinach stew", "29.99"}
        ));

        // Groceries products
        productsByCategory.put("African Groceries", List.of(
            new String[]{"Palm Oil (1L)", "Pure red palm oil", "15.99"},
            new String[]{"Egusi Seeds (500g)", "Ground melon seeds", "12.99"},
            new String[]{"Garri (2 lbs)", "Cassava flakes for eba", "8.99"},
            new String[]{"Dried Fish", "Assorted dried fish (8 oz)", "18.99"}
        ));

        List<String[]> products = productsByCategory.getOrDefault(category.getName(), List.of());

        for (int i = 0; i < Math.min(products.size(), 5); i++) {
            String[] productData = products.get(i);

            Product product = Product.builder()
                    .name(productData[0])
                    .description(productData[1])
                    .price(new BigDecimal(productData[2]))
                    .imageUrl(FOOD_IMAGES[(vendorIndex + i) % FOOD_IMAGES.length])
                    .available(true)
                    .preparationTimeMinutes(15 + (i * 5))
                    .vendor(vendor)
                    .category(category)
                    .build();

            productRepository.save(product);
        }
    }

    private void createRealisticReviews(VendorProfile vendor, double targetRating, int vendorIndex) {
        String[] customerNames = {
            "John Doe", "Jane Smith", "Mike Johnson", "Sarah Williams", "David Brown",
            "Emma Davis", "James Wilson", "Olivia Taylor", "Robert Anderson", "Sophia Thomas"
        };

        String[] positiveComments = {
            "Excellent food and great service!",
            "Authentic taste, just like home!",
            "Best African food in Calgary!",
            "Always fresh and delicious",
            "Highly recommend this place!"
        };

        String[] neutralComments = {
            "Good food, average service",
            "Decent portions",
            "It's okay, nothing special"
        };

        String[] productPositiveComments = {
            "This dish is absolutely delicious!",
            "Perfect portion size and amazing flavor!",
            "Best version of this dish I've had!",
            "Will definitely order this again!",
            "Exceeded my expectations!"
        };

        String[] productNeutralComments = {
            "It was okay, nothing extraordinary",
            "Decent taste, could use more seasoning",
            "Average, expected more"
        };

        // Get vendor's products for product-specific reviews
        List<Product> vendorProducts = productRepository.findByVendor(vendor);

        int reviewCount = 3 + (vendorIndex % 3);
        for (int i = 0; i < reviewCount; i++) {
            User reviewer = createReviewerUser(customerNames[i % customerNames.length], vendorIndex, i);

            int rating = targetRating >= 4.5 ? 5 : (targetRating >= 4.0 ? 4 : 3);

            // Alternate between vendor reviews and product reviews
            boolean isProductReview = i % 2 == 0 && !vendorProducts.isEmpty();

            String comment;
            Product reviewProduct = null;

            if (isProductReview) {
                // Create a product-specific review
                reviewProduct = vendorProducts.get(i % vendorProducts.size());
                comment = rating >= 4 ?
                    productPositiveComments[i % productPositiveComments.length] :
                    productNeutralComments[i % productNeutralComments.length];
            } else {
                // Create a general vendor review
                comment = rating >= 4 ?
                    positiveComments[i % positiveComments.length] :
                    neutralComments[i % neutralComments.length];
            }

            Review review = Review.builder()
                    .user(reviewer)
                    .vendor(vendor)
                    .product(reviewProduct) // Will be null for vendor reviews
                    .rating(rating)
                    .comment(comment)
                    .isVisible(true)
                    .helpfulCount(i % 5)
                    .build();

            reviewRepository.save(review);
        }
    }

    private User createReviewerUser(String userName, int vendorIndex, int reviewIndex) {
        String email = userName.toLowerCase().replace(" ", ".") + vendorIndex + reviewIndex + "@customer.com";

        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = User.builder()
                            .email(email)
                            .password(passwordEncoder.encode("password123"))
                            .firstName(userName.split(" ")[0])
                            .lastName(userName.split(" ").length > 1 ? userName.split(" ")[1] : "User")
                            .phone(generateCanadianPhoneNumber(2000 + vendorIndex * 10 + reviewIndex)) // Offset by 2000 for reviewers
                            .role(Role.CUSTOMER)
                            .emailVerified(true)
                            .isActive(true)
                            .build();
                    return userRepository.save(user);
                });
    }
}
