package com.afrochow.common.util;

/**
 * Plain-Java great-circle distance calculation. Deliberately NOT dependent on
 * Redis/the vendor geo index — used where distance needs to be evaluated
 * regardless of whether Redis is up, e.g. order-time delivery-range checks,
 * which must not be allowed to fail an order just because a cache is down.
 *
 * Same haversine approach as the existing JPQL query in
 * ProductRepository.findProductsNearCoordinates and the Redis GEO commands
 * used elsewhere in the app — kept here as the one plain-Java implementation
 * for callers that can't or shouldn't route through Redis.
 */
public final class GeoDistanceUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceUtils() {
    }

    /**
     * Great-circle distance between two lat/lng points, in kilometers.
     * Returns null if any coordinate is null (caller decides how to handle
     * missing/ungeocoded addresses — this util never guesses).
     */
    public static Double distanceKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
