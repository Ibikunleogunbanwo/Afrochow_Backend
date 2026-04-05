package com.afrochow.order.event;

/**
 * Published immediately after a customer's order is persisted and payment is
 * authorised, once the placing transaction commits.
 *
 * This gives the customer instant feedback that their order was received and is
 * waiting for the vendor to confirm — before the vendor has had a chance to act.
 */
public record CustomerOrderReceivedEvent(String publicOrderId) {}
