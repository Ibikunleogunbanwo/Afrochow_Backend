package com.afrochow.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate order counts for the admin Orders dashboard's stat cards.
 * Computed server-side in a single grouped query (see
 * {@code OrderRepository#countGroupedByStatus}) rather than the frontend
 * bucketing a client-fetched, status-filtered list — that approach made
 * every card except the currently-selected tab's silently read 0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatsDto {
    private long total;
    private long pending;
    private long confirmed;
    private long preparing;
    private long readyForPickup;
    private long outForDelivery;
    private long delivered;
    private long cancelled;
    private long refunded;
    /** Every non-terminal status (everything except DELIVERED/CANCELLED/REFUNDED). */
    private long active;
}
