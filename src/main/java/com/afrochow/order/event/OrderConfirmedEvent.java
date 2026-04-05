package com.afrochow.order.event;

/**
 * Published after a vendor accepts an order and the confirming transaction commits.
 * Listeners use @TransactionalEventListener(phase = AFTER_COMMIT) so they only
 * fire once the CONFIRMED status is visible to all DB readers.
 */
public record OrderConfirmedEvent(String publicOrderId) {}
