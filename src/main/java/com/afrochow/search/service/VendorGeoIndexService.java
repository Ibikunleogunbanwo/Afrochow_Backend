package com.afrochow.search.service;

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
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    // Generous radius for distance-label lookups (not a "near me" search radius) —
    // wide enough to cover any vendor within the same city/region as the user so a
    // requested vendor's distance is essentially always resolved, while still keeping
    // the underlying GEORADIUS query bounded.
    @Value("${app.redis.geo.distance-lookup-radius-km:300}")
    private double distanceLookupRadiusKm;

    // Outer bound for "near me" discovery searches — wide enough that a vendor who's
    // configured a maxDeliveryDistanceKm larger than the platform default still gets
    // found. The real per-vendor cutoff is applied afterward in findNearbyVendors:
    // each vendor's OWN maxDeliveryDistanceKm (when they offer delivery and set one)
    // takes precedence over the caller's requested radiusKm, which only applies as a
    // fallback default for vendors who haven't configured a range (e.g. pickup-only,
    // or delivery vendors who left it unset) — MVP intentionally favors inclusivity
    // here rather than hiding vendors over a missing setting.
    @Value("${app.redis.geo.near-me-search-radius-km:50}")
    private double nearMeSearchRadiusKm;

    // Safety-valve cap on the Redis-side GEORADIUS result count for "near me"
    // discovery searches. Per-vendor filtering (each vendor's own
    // maxDeliveryDistanceKm) happens in Java AFTER this query, so this needs to
    // be comfortably larger than the final `limit` callers ask for — otherwise
    // Redis could hand back exactly `limit` candidates and every single one
    // could get filtered out by the per-vendor cutoff, leaving fewer results
    // than a dense area actually has available. Default of 300 is generous for
    // current MVP vendor density; revisit upward if a metro area's active
    // vendor count within nearMeSearchRadiusKm approaches this.
    @Value("${app.redis.geo.near-me-search-limit:300}")
    private int nearMeSearchLimit;

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

    /**
     * Find vendors near a point, ranked by popularity/quality (see
     * {@link #popularityScore}) with distance as a tiebreaker, up to {@code limit}.
     * Distance is still a hard gate — a vendor has to be within delivery range to
     * appear at all — it's just no longer the sort key once that's true.
     *
     * {@code radiusKm} is used as the fallback distance cap for vendors who haven't
     * configured their own {@code maxDeliveryDistanceKm} (or who are pickup-only) —
     * it is NOT the Redis search radius itself. The underlying GEORADIUS query always
     * searches out to {@code nearMeSearchRadiusKm} (a wide outer bound) so that a
     * delivery-enabled vendor with a larger-than-default range is still found; each
     * vendor is then kept only if the real distance is within THEIR OWN
     * maxDeliveryDistanceKm (when set and delivery is offered) or, failing that,
     * within the caller's requested {@code radiusKm}.
     */
    public List<VendorProfile> findNearbyVendors(double lat, double lng, double radiusKm, int limit) {
        if (!enabled) {
            return Collections.emptyList();
        }

        try {
            // Redis-side cap comfortably above `limit` — see nearMeSearchLimit's doc
            // comment for why this can't just be `limit` itself.
            int searchLimit = Math.max(limit * 10, nearMeSearchLimit);

            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance()
                    .sortAscending()
                    .limit(searchLimit);

            double searchRadiusKm = Math.max(radiusKm, nearMeSearchRadiusKm);

            GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(
                    vendorGeoKey,
                    new Circle(new Point(lng, lat), new Distance(searchRadiusKm, Metrics.KILOMETERS)),
                    args);

            if (results == null || results.getContent().isEmpty()) {
                return Collections.emptyList();
            }

            // LinkedHashMap preserves Redis's ascending-distance order while keeping
            // each vendor's actual distance for the per-vendor cutoff check below.
            Map<String, Double> distanceByVendorId = new LinkedHashMap<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
                String vendorId = result.getContent().getName();
                if (vendorId != null) {
                    distanceByVendorId.put(vendorId, result.getDistance().getValue());
                }
            }

            Map<String, VendorProfile> vendorsByPublicId = vendorProfileRepository
                    .findActiveVerifiedVendorsByPublicUserIds(new ArrayList<>(distanceByVendorId.keySet()))
                    .stream()
                    .collect(Collectors.toMap(
                            VendorProfile::getPublicVendorId,
                            Function.identity(),
                            (existing, ignored) -> existing));

            // Rank by popularity/quality within delivery range, not raw distance —
            // distance is still the *gate* (a vendor that can't deliver to you is
            // filtered out below via effectiveRadiusKm), but once a vendor clears
            // that bar, being 200m closer shouldn't put an unproven, zero-review
            // vendor ahead of a highly-rated, high-volume one. Distance only breaks
            // ties between similarly-scored vendors (see popularityScore javadoc).
            Comparator<Map.Entry<VendorProfile, Double>> byPopularityThenDistance = Comparator
                    .comparingDouble((Map.Entry<VendorProfile, Double> e) -> popularityScore(e.getKey()))
                    .reversed()
                    .thenComparingDouble(Map.Entry::getValue);

            return distanceByVendorId.entrySet().stream()
                    .map(entry -> {
                        VendorProfile vendor = vendorsByPublicId.get(entry.getKey());
                        if (vendor == null) return null;

                        double effectiveRadiusKm = (Boolean.TRUE.equals(vendor.getOffersDelivery())
                                && vendor.getMaxDeliveryDistanceKm() != null)
                                ? vendor.getMaxDeliveryDistanceKm().doubleValue()
                                : radiusKm;

                        if (entry.getValue() > effectiveRadiusKm) return null;
                        return Map.entry(vendor, entry.getValue());
                    })
                    .filter(Objects::nonNull)
                    .sorted(byPopularityThenDistance)
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (Exception e) {
            log.warn("vendor.geo.index.lookup_failed key={} lat={} lng={} radiusKm={}",
                    vendorGeoKey, lat, lng, radiusKm, e);
            return Collections.emptyList();
        }
    }

    /**
     * Popularity/quality score used to rank "near you" results — rating weighted
     * by log(1 + completed orders), so a single 5-star review can't outrank a
     * vendor with hundreds of proven orders, while a high-volume vendor with a
     * merely-decent rating still beats an unproven one. Only used as the primary
     * sort key in findNearbyVendors; raw distance still breaks ties, and every
     * candidate is already filtered to its own delivery radius before this runs,
     * so ranking by popularity never means "can't actually deliver to you."
     * Brand-new vendors (no orders, no reviews) score 0 and sort by distance
     * among themselves rather than being hidden entirely.
     */
    private double popularityScore(VendorProfile vendor) {
        double rating = vendor.getAverageRating();
        int orders = vendor.getTotalOrdersCompleted() != null ? vendor.getTotalOrdersCompleted() : 0;
        return rating * Math.log(1 + orders);
    }

    /**
     * Resolve distance-from-point (in km) for a specific set of vendors, computed
     * entirely by Redis (GEORADIUS/GEOSEARCH), not reimplemented in application code.
     *
     * The requested vendor IDs must already be members of the geo index (populated by
     * {@link #indexVendor}/{@link #rebuildVendorIndex}). This does one bounded radius
     * query around the point and keeps only the distances for the requested IDs —
     * there's no native Redis command for "distance from an arbitrary point to N
     * specific known members" in a single round trip, so a generous radius stands in
     * for that. Vendors outside {@code distanceLookupRadiusKm} or not indexed (e.g.
     * missing geocoded coordinates) are simply absent from the returned map.
     *
     * @return map of vendorPublicId -> distanceKm for whichever requested vendors
     *         were resolved. Empty map if Redis is disabled/unavailable or no
     *         requested vendor falls within range.
     */
    public Map<String, Double> getDistancesKm(double lat, double lng, List<String> vendorPublicIds) {
        if (!enabled || vendorPublicIds == null || vendorPublicIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Set<String> requested = new HashSet<>(vendorPublicIds);

            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeDistance();

            GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(
                    vendorGeoKey,
                    new Circle(new Point(lng, lat), new Distance(distanceLookupRadiusKm, Metrics.KILOMETERS)),
                    args);

            if (results == null || results.getContent().isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, Double> distances = new HashMap<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
                String vendorId = result.getContent().getName();
                if (vendorId != null && requested.contains(vendorId)) {
                    distances.put(vendorId, result.getDistance().getValue());
                }
            }
            return distances;
        } catch (Exception e) {
            log.warn("vendor.geo.index.distance_lookup_failed key={} lat={} lng={} radiusKm={}",
                    vendorGeoKey, lat, lng, distanceLookupRadiusKm, e);
            return Collections.emptyMap();
        }
    }
}
