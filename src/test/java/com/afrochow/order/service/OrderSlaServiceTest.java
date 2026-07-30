package com.afrochow.order.service;

import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderSlaService is a thin scheduled sweep: find PENDING orders past the
 * acceptance window and hand each to OrderService.autoExpireOrder(). These
 * tests exercise the sweep logic (cutoff calculation, empty-result short
 * circuit, per-order error isolation) — not autoExpireOrder itself, which is
 * already covered in OrderServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class OrderSlaServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    @InjectMocks
    private OrderSlaService orderSlaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderSlaService, "acceptWindowMinutes", 10);
    }

    private Order orderWithId(String publicOrderId) {
        return Order.builder().publicOrderId(publicOrderId).orderTime(LocalDateTime.now().minusMinutes(15)).build();
    }

    @Test
    void expireStaleOrders_noneExpired_shortCircuitsWithoutCallingAutoExpire() {
        when(orderRepository.findExpiredPendingOrders(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        orderSlaService.expireStaleOrders();

        verify(orderService, never()).autoExpireOrder(any());
    }

    @Test
    void expireStaleOrders_usesCutoffDerivedFromConfiguredWindow() {
        when(orderRepository.findExpiredPendingOrders(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        LocalDateTime before = LocalDateTime.now().minusMinutes(10);
        orderSlaService.expireStaleOrders();
        LocalDateTime after = LocalDateTime.now().minusMinutes(10);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findExpiredPendingOrders(captor.capture());
        LocalDateTime cutoff = captor.getValue();

        // cutoff = now - 10min, computed inside the method call — bound it within a
        // generous window either side of our own before/after snapshots.
        assertThat(cutoff).isBetween(before.minus(5, ChronoUnit.SECONDS), after.plus(5, ChronoUnit.SECONDS));
    }

    @Test
    void expireStaleOrders_multipleExpired_autoExpiresEachOne() {
        Order order1 = orderWithId("AFC-ONE0001");
        Order order2 = orderWithId("AFC-TWO0002");
        when(orderRepository.findExpiredPendingOrders(any(LocalDateTime.class)))
                .thenReturn(List.of(order1, order2));

        orderSlaService.expireStaleOrders();

        verify(orderService).autoExpireOrder(order1);
        verify(orderService).autoExpireOrder(order2);
        verify(orderService, times(2)).autoExpireOrder(any());
    }

    @Test
    void expireStaleOrders_oneOrderFailsAutoExpire_stillProcessesTheRest() {
        Order failing = orderWithId("AFC-FAIL0001");
        Order healthy = orderWithId("AFC-OK000001");
        when(orderRepository.findExpiredPendingOrders(any(LocalDateTime.class)))
                .thenReturn(List.of(failing, healthy));
        doThrow(new RuntimeException("Stripe timeout")).when(orderService).autoExpireOrder(failing);

        // Must not propagate — a single bad order shouldn't kill the whole sweep,
        // since the cycle just retries unresolved orders next run.
        orderSlaService.expireStaleOrders();

        verify(orderService).autoExpireOrder(failing);
        verify(orderService).autoExpireOrder(healthy);
    }
}
