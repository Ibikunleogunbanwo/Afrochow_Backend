package com.afrochow.order.event;

/**
 * Published after a new order is persisted and the placing transaction commits.
 * Listeners use @TransactionalEventListener(phase = AFTER_COMMIT) so they only
 * fire once the order is visible to all DB readers.
 */
public record OrderPlacedEvent(String publicOrderId) {}
