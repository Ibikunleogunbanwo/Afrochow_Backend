package com.afrochow.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Allowed store/product types for vendor profiles.
 * The stored DB value is the display label (a plain String column — no enum column migration needed).
 * This enum acts as the authoritative list of valid choices exposed to the frontend.
 */
@Getter
@RequiredArgsConstructor
public enum CuisineType {

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
    public static CuisineType fromLabel(String label) {
        if (label == null) return null;
        for (CuisineType ct : values()) {
            if (ct.label.equalsIgnoreCase(label.trim())) return ct;
        }
        return null;
    }

    /** Returns just the display labels — what the frontend dropdown needs. */
    public static java.util.List<String> labels() {
        return java.util.Arrays.stream(values())
                .map(CuisineType::getLabel)
                .toList();
    }
}
