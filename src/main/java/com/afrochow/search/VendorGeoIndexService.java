package com.afrochow.search;

import com.afrochow.address.model.Address;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorGeoIndexService {

    private final StringRedisTemplate redisTemplate;
    private final VendorProfileRepository vendorProfileRepository;

    @Value("${app.redis.geo.vendor-index.enabled:true}")
    private boolean enabled;

    @Value("${app.redis.geo.vendor-index.key:afrochow:geo:vendors}")
    private String vendorGeoKey;

    @Scheduled(cron = "${app.redis.geo.vendor-index-refresh-cron:0 */10 * * * *}")
    @Transactional(readOnly = true)
    public void rebuildVendorIndex() {
        if (!enabled) {
            return;
        }

        try {
            List<VendorProfile> vendors = vendorProfileRepository.findActiveVerifiedGeocodedVendors();
            redisTemplate.delete(vendorGeoKey);

            int indexed = 0;
            for (VendorProfile vendor : vendors) {
                if (indexVendor(vendor)) {
                    indexed++;
                }
            }

            log.info("vendor.geo.index.rebuilt key={} indexed={}", vendorGeoKey, indexed);
        } catch (Exception e) {
            log.warn("vendor.geo.index.rebuild_failed key={}", vendorGeoKey, e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void rebuildVendorIndexOnStartup() {
        rebuildVendorIndex();
    }

    public boolean indexVendor(VendorProfile vendor) {
        if (!enabled || vendor == null || vendor.getPublicVendorId() == null) {
            return false;
        }

        Address address = vendor.getAddress();
        if (address == null || address.getLatitude() == null || address.getLongitude() == null) {
            return false;
        }

        redisTemplate.opsForGeo().add(
                vendorGeoKey,
                new Point(address.getLongitude(), address.getLatitude()),
                vendor.getPublicVendorId());
        return true;
    }

    public List<VendorProfile> findNearbyVendors(double lat, double lng, double radiusKm, int limit) {
        if (!enabled) {
            return Collections.emptyList();
        }

        try {
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance()
                    .sortAscending()
                    .limit(limit);

            GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(
                    vendorGeoKey,
                    new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                    args);

            if (results == null || results.getContent().isEmpty()) {
                return Collections.emptyList();
            }

            List<String> publicVendorIds = results.getContent().stream()
                    .map(result -> result.getContent().getName())
                    .filter(Objects::nonNull)
                    .toList();

            Map<String, VendorProfile> vendorsByPublicId = vendorProfileRepository
                    .findActiveVerifiedVendorsByPublicUserIds(publicVendorIds)
                    .stream()
                    .collect(Collectors.toMap(
                            VendorProfile::getPublicVendorId,
                            Function.identity(),
                            (existing, ignored) -> existing));

            return publicVendorIds.stream()
                    .map(vendorsByPublicId::get)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("vendor.geo.index.lookup_failed key={} lat={} lng={} radiusKm={}",
                    vendorGeoKey, lat, lng, radiusKm, e);
            return Collections.emptyList();
        }
    }
}
