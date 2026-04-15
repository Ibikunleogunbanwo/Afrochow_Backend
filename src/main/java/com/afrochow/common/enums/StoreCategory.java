package com.afrochow.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Allowed store/product categories for vendor profiles.
 *
 * Covers the full variety of goods on the platform — restaurants, grocery
 * stores, farm produce, bakeries, catering, and more.
 *
 * The stored DB value is the display label (a plain String column — no enum
 * column migration needed). This enum acts as the authoritative list of valid
 * choices exposed to the frontend.
 */
@Getter
@RequiredArgsConstructor
public enum StoreCategory {

    AFRICAN_HOME_KITCHEN   ("African Home Kitchen"),
    AFRICAN_RESTAURANT     ("African Restaurant"),
    AFRICAN_SOUPS_AND_STEWS("African Soups & Stews"),
    AFRICAN_GROCERY_STORE  ("African Grocery Store"),
    BAKERY_AND_PASTRIES    ("Bakery & Pastries"),
    FARM_PRODUCE           ("Farm Produce"),
    CATERING_SERVICES      ("Catering Services"),
    CARIBBEAN_CUISINE      ("Caribbean Cuisine"),
    FROZEN_MEALS           ("Frozen Meals & Meal Prep"),
    HALAL_FOOD             ("Halal Food"),
    OTHER                  ("Other");

    /** The human-readable label stored in the DB and sent to the frontend. */
    private final String label;

    /** Serialise as the label string, not the enum name. */
    @JsonValue
    public String getLabel() {
        return label;
    }

    /** Find by label (case-insensitive). Used for validation. */
    public static StoreCategory fromLabel(String label) {
        if (label == null) return null;
        for (StoreCategory sc : values()) {
            if (sc.label.equalsIgnoreCase(label.trim())) return sc;
        }
        return null;
    }

    /** Returns just the display labels — what the frontend dropdown needs. */
    public static java.util.List<String> labels() {
        return java.util.Arrays.stream(values())
                .map(StoreCategory::getLabel)
                .toList();
    }
}
