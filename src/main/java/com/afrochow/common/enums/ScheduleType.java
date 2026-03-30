package com.afrochow.common.enums;

/**
 * Determines when a product can be fulfilled.
 * SAME_DAY    — ready within the product's preparationTimeMinutes; no scheduling needed.
 * ADVANCE_ORDER — must be placed at least advanceNoticeHours before the desired fulfillment time.
 */
public enum ScheduleType {
    SAME_DAY,
    ADVANCE_ORDER
}
