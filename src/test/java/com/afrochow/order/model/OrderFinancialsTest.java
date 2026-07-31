package com.afrochow.order.model;

import com.afrochow.orderline.model.OrderLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the order money arithmetic — specifically that tax is charged on the
 * DISCOUNTED consideration.
 *
 * <p>These are the numbers the customer's card is charged and that the payout split is
 * derived from, and they were previously computed off the pre-discount list price. That
 * overcharged tax on every promo order and handed the vendor tax to remit on revenue
 * nobody received.
 */
class OrderFinancialsTest {

    /** lineTotal is derived (price x quantity), so a single unit at the target price. */
    private Order orderWith(String lineTotal, String deliveryFee, String taxRate) {
        Order order = Order.builder()
                .taxRate(new BigDecimal(taxRate))
                .deliveryFee(new BigDecimal(deliveryFee))
                .build();
        order.setOrderLines(List.of(OrderLine.builder()
                .priceAtPurchase(new BigDecimal(lineTotal))
                .quantity(1)
                .build()));
        return order;
    }

    @Test
    void noDiscount_taxesTheFullSubtotalAndDelivery() {
        Order order = orderWith("100.00", "5.00", "0.13");

        // 13% of (100 + 5)
        assertThat(order.calculateTax()).isEqualByComparingTo("13.65");
        assertThat(order.calculateTotal()).isEqualByComparingTo("118.65");
    }

    @Test
    void foodDiscount_taxesOnlyWhatTheCustomerActuallyPays() {
        Order order = orderWith("100.00", "5.00", "0.13");
        order.setFoodDiscount(new BigDecimal("20.00"));
        order.setDiscount(new BigDecimal("20.00"));

        // 13% of (80 + 5) = 11.05, NOT 13% of (100 + 5) = 13.65
        assertThat(order.calculateTax()).isEqualByComparingTo("11.05");
        assertThat(order.calculateTotal()).isEqualByComparingTo("96.05");
        assertThat(order.effectiveSubtotal()).isEqualByComparingTo("80.00");
    }

    @Test
    void freeDeliveryPromo_removesDeliveryFromTheTaxBaseButNotTheFood() {
        Order order = orderWith("100.00", "5.00", "0.13");
        order.setDeliveryDiscount(new BigDecimal("5.00"));
        order.setDiscount(new BigDecimal("5.00"));

        assertThat(order.effectiveSubtotal()).isEqualByComparingTo("100.00");
        assertThat(order.effectiveDeliveryFee()).isEqualByComparingTo("0.00");
        // 13% of 100
        assertThat(order.calculateTax()).isEqualByComparingTo("13.00");
        assertThat(order.calculateTotal()).isEqualByComparingTo("113.00");
    }

    /**
     * A discount is clamped to the subtotal upstream, but the model must not produce a
     * negative charge even if that ever changes — Stripe rejects a negative amount.
     */
    @Test
    void discountExceedingSubtotal_flooredAtZeroNeverNegative() {
        Order order = orderWith("50.00", "0.00", "0.13");
        order.setFoodDiscount(new BigDecimal("80.00"));
        order.setDiscount(new BigDecimal("80.00"));

        assertThat(order.effectiveSubtotal()).isEqualByComparingTo("0.00");
        assertThat(order.calculateTax()).isEqualByComparingTo("0.00");
        assertThat(order.calculateTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void pickupOrder_noDeliveryFeeInTaxBase() {
        Order order = orderWith("100.00", "0.00", "0.05");

        assertThat(order.calculateTax()).isEqualByComparingTo("5.00");
        assertThat(order.calculateTotal()).isEqualByComparingTo("105.00");
    }
}
