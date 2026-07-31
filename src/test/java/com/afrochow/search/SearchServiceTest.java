package com.afrochow.search;

import com.afrochow.address.model.Address;
import com.afrochow.product.dto.ProductResponseDto;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.user.model.User;
import com.afrochow.vendor.VendorMapper;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private ProductRepository productRepository;
    @Mock private VendorMapper vendorMapper;
    @Mock private VendorGeoIndexService vendorGeoIndexService;

    @InjectMocks private SearchService searchService;

    @Test
    void getSimilarProductsWithCoordinatesOnlyReturnsNearbyMatches() {
        Product source = product("source-product", "Vanilla Wedding Cake", sourceVendor());
        Product nearby = product("nearby-product", "Vanilla Wedding Cake", vendor(2L, "nearby-vendor", "Calgary"));
        Product farAway = product("far-product", "Vanilla Wedding Cake", vendor(3L, "far-vendor", "Toronto"));

        when(productRepository.findByPublicProductId("source-product")).thenReturn(Optional.of(source));
        when(productRepository.findSameNameAtOtherVendors("Vanilla Wedding Cake", 1L))
                .thenReturn(List.of(nearby, farAway));
        when(vendorGeoIndexService.findNearbyVendors(51.05, -114.07, 25, 100))
                .thenReturn(List.of(nearby.getVendor()));
        when(vendorGeoIndexService.getDistancesKm(51.05, -114.07, List.of("nearby-vendor")))
                .thenReturn(Map.of("nearby-vendor", 4.2));

        List<ProductResponseDto> results = searchService.getSimilarProducts(
                "source-product", null, 51.05, -114.07);

        assertThat(results).extracting(ProductResponseDto::getPublicProductId)
                .containsExactly("nearby-product");
        assertThat(results.getFirst().getDistanceKm()).isEqualTo(4.2);
    }

    @Test
    void getSimilarProductsWithCityOnlyReturnsSameCityMatches() {
        Product source = product("source-product", "Vanilla Wedding Cake", sourceVendor());
        Product calgary = product("calgary-product", "Vanilla Wedding Cake", vendor(2L, "calgary-vendor", "Calgary"));
        Product toronto = product("toronto-product", "Vanilla Wedding Cake", vendor(3L, "toronto-vendor", "Toronto"));

        when(productRepository.findByPublicProductId("source-product")).thenReturn(Optional.of(source));
        when(productRepository.findSameNameAtOtherVendors("Vanilla Wedding Cake", 1L))
                .thenReturn(List.of(calgary, toronto));

        List<ProductResponseDto> results = searchService.getSimilarProducts(
                "source-product", "Calgary", null, null);

        assertThat(results).extracting(ProductResponseDto::getPublicProductId)
                .containsExactly("calgary-product");
    }

    @Test
    void getSimilarProductsWithoutLocationFallsBackToSourceVendorCity() {
        Product source = product("source-product", "Vanilla Wedding Cake", sourceVendor());
        Product calgary = product("calgary-product", "Vanilla Wedding Cake", vendor(2L, "calgary-vendor", "Calgary"));
        Product toronto = product("toronto-product", "Vanilla Wedding Cake", vendor(3L, "toronto-vendor", "Toronto"));

        when(productRepository.findByPublicProductId("source-product")).thenReturn(Optional.of(source));
        when(productRepository.findSameNameAtOtherVendors("Vanilla Wedding Cake", 1L))
                .thenReturn(List.of(toronto, calgary));

        List<ProductResponseDto> results = searchService.getSimilarProducts(
                "source-product", null, null, null);

        assertThat(results).extracting(ProductResponseDto::getPublicProductId)
                .containsExactly("calgary-product");
    }

    private Product product(String publicProductId, String name, VendorProfile vendor) {
        return Product.builder()
                .publicProductId(publicProductId)
                .name(name)
                .price(new BigDecimal("75.99"))
                .available(true)
                .adminVisible(true)
                .vendor(vendor)
                .build();
    }

    private VendorProfile sourceVendor() {
        return vendor(1L, "source-vendor", "Calgary");
    }

    private VendorProfile vendor(Long id, String publicVendorId, String city) {
        return VendorProfile.builder()
                .id(id)
                .user(User.builder().publicUserId(publicVendorId).build())
                .restaurantName(publicVendorId)
                .address(Address.builder()
                        .addressLine("1 Test St")
                        .city(city)
                        .postalCode("T1T1T1")
                        .country("Canada")
                        .build())
                .build();
    }
}
