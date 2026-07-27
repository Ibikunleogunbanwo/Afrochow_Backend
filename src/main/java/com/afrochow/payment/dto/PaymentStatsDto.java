package com.afrochow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate payment counts for the admin Payment Management dashboard's stat
 * cards. Computed server-side in a single grouped query (see
 * {@code PaymentRepository#countGroupedByStatus}) rather than the frontend
 * fetching every payment row and bucketing them in JS — that approach is what
 * originally let a status value (CANCELLED) fall through the cracks with no
 * card/filter of its own, silently making {@code total} exceed the sum of the
 * other buckets.
 *
 * <p>Every {@code PaymentStatus} value gets an explicit field on purpose —
 * add a new enum value here too when one is added to the backend, so this
 * class can't silently drop a bucket the way the old client-side computation did.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatsDto {
    private long total;
    private long pending;
    private long authorized;
    private long completed;
    private long failed;
    private long refunded;
    private long cancelled;
}
