package com.afrochow.order.service;

import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.common.enums.VendorStatus;
import com.afrochow.common.exceptions.DeliveryOutOfRangeException;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.order.dto.OrderRequestDto;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.orderline.dto.OrderLineRequestDto;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import com.afrochow.payment.service.PaymentService;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.promotion.service.PromotionService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private PromotionService promotionService;
    @Mock private OutboxEventService outboxEventService;

    @InjectMocks
    private OrderService orderService;

    private User customerUser;
    private CustomerProfile customer;
    private User vendorUser;
    private VendorProfile vendor;
    private Address vendorAddress;
    private Address deliveryAddress;
    private Product product;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "self", orderService);
        ReflectionTestUtils.setField(orderService, "slaAcceptWindowMinutes", 10);
        ReflectionTestUtils.setField(orderService, "cancellationWindowHours", 6);

        customerUser = User.builder().userId(1L).publicUserId("CUS123")
                .firstName("Ade").lastName("O").build();
        customer = CustomerProfile.builder().customerProfileId(10L).user(customerUser).build();

        vendorUser = User.builder().userId(2L).publicUserId("VEN123").build();
        vendorAddress = Address.builder().publicAddressId("ADDR-VEN").city("Calgary")
                .province(Province.AB).latitude(51.0500).longitude(-114.0700).build();

        product = Product.builder().productId(100L).publicProductId("PROD-1")
                .name("Jollof Rice").price(new BigDecimal("15.00")).available(true)
                .scheduleType(ScheduleType.SAME_DAY).preparationTimeMinutes(20).build();

        // A second, always-available product so tests that flip `product.available`
        // to exercise the order-line validation don't also flip
        // VendorProfile.hasActiveProducts() (and therefore canReceiveOrders()) —
        // that's a separate, unrelated check keyed off the whole products list.
        Product fillerProduct = Product.builder().productId(101L).publicProductId("PROD-2")
                .name("Filler Item").price(new BigDecimal("5.00")).available(true)
                .scheduleType(ScheduleType.SAME_DAY).build();

        vendor = VendorProfile.builder().id(5L).user(vendorUser).restaurantName("Jollof House")
                .vendorStatus(VendorStatus.VERIFIED).address(vendorAddress)
                .offersDelivery(true).deliveryFee(new BigDecimal("5.00"))
                .maxDeliveryDistanceKm(new BigDecimal("10.0"))
                // Order.getEstimatedDeliveryTime() unboxes this directly — leaving it null
                // would NPE the moment any DTO-returning method maps a saved Order with a
                // non-null orderTime (i.e. every sampleOrder()-based test below).
                .estimatedDeliveryMinutes(30)
                .products(List.of(product, fillerProduct))
                .build();
        product.setVendor(vendor);
        fillerProduct.setVendor(vendor);

        deliveryAddress = Address.builder().publicAddressId("ADDR-CUST").city("Calgary")
                .province(Province.AB).customerProfile(customer)
                .latitude(51.0520).longitude(-114.0680).build();

        // Simulates JPA's @PrePersist/@PreUpdate financial recalculation, which never
        // fires in a plain unit test since there is no real EntityManager involved.
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getPublicOrderId() == null) o.setPublicOrderId("AFC-TEST0001");
            o.setSubtotal(o.calculateSubtotal());
            o.setTax(o.calculateTax());
            o.setTotalAmount(o.calculateTotal());
            return o;
        });
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrderRequestDto.OrderRequestDtoBuilder pickupRequest() {
        return OrderRequestDto.builder()
                .vendorPublicId("VEN123")
                .fulfillmentType("PICKUP")
                .paymentMethodId("pm_123")
                .orderLines(List.of(OrderLineRequestDto.builder()
                        .productPublicId("PROD-1").quantity(2).build()));
    }

    private Order sampleOrder(OrderStatus status) {
        Order order = Order.builder()
                .orderId(500L)
                .publicOrderId("AFC-EXIST01")
                .customer(customer)
                .vendor(vendor)
                .fulfillmentType("PICKUP")
                .status(status)
                .totalAmount(new BigDecimal("30.00"))
                .subtotal(new BigDecimal("30.00"))
                .orderTime(LocalDateTime.now())
                .orderLines(List.of(OrderLine.builder().product(product).quantity(2)
                        .priceAtPurchase(new BigDecimal("15.00"))
                        .productNameAtPurchase("Jollof Rice").build()))
                .build();
        return order;
    }

    // ========== createOrder ==========

    @Test
    void createOrder_pickupSuccess_authorizesPaymentAndFiresOrderPlacedEvents() {
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(paymentService.chargeOrder(any(Order.class), eq("pm_123")))
                .thenReturn(new PaymentService.ChargeOutcome(PaymentStatus.AUTHORIZED, null, null));

        OrderResponseDto response = orderService.createOrder(1L, pickupRequest().build());

        assertThat(response.getRequiresAction()).isNull();
        // subtotal 2x$15.00 = $30.00, pickup so no delivery fee, +5% AB GST = $31.50
        assertThat(response.getTotalAmount()).isEqualByComparingTo("31.50");
        verify(outboxEventService).orderPlaced("AFC-TEST0001");
        verify(outboxEventService).customerOrderReceived("AFC-TEST0001");
    }

    @Test
    void createOrder_deliverySuccess_appliesDeliveryFeeAndTaxProvince() {
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(addressRepository.findByPublicAddressId("ADDR-CUST")).thenReturn(Optional.of(deliveryAddress));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(paymentService.chargeOrder(any(Order.class), eq("pm_123")))
                .thenReturn(new PaymentService.ChargeOutcome(PaymentStatus.AUTHORIZED, null, null));

        OrderRequestDto request = pickupRequest()
                .fulfillmentType("DELIVERY")
                .deliveryAddressPublicId("ADDR-CUST")
                .build();

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        orderService.createOrder(1L, request);

        verify(orderRepository, times(1)).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getDeliveryFee()).isEqualByComparingTo("5.00");
        assertThat(saved.getTaxProvince()).isEqualTo("AB");
    }

    @Test
    void createOrder_vendorCannotReceiveOrders_throwsIllegalState() {
        vendor.setVendorStatus(VendorStatus.PENDING_REVIEW); // not PROVISIONAL/VERIFIED
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> orderService.createOrder(1L, pickupRequest().build()))
                .isInstanceOf(IllegalStateException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_deliveryMissingAddressId_throwsIllegalArgument() {
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));

        OrderRequestDto request = pickupRequest().fulfillmentType("DELIVERY").build();

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deliveryAddressPublicId");
    }

    @Test
    void createOrder_deliveryAddressNotOwnedByCustomer_throwsIllegalState() {
        CustomerProfile otherCustomer = CustomerProfile.builder().customerProfileId(99L).build();
        deliveryAddress.setCustomerProfile(otherCustomer);

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(addressRepository.findByPublicAddressId("ADDR-CUST")).thenReturn(Optional.of(deliveryAddress));

        OrderRequestDto request = pickupRequest()
                .fulfillmentType("DELIVERY").deliveryAddressPublicId("ADDR-CUST").build();

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void createOrder_deliveryOutOfRange_throwsDeliveryOutOfRangeException() {
        deliveryAddress.setLatitude(60.0); // far away from vendor's 51.05
        deliveryAddress.setLongitude(-100.0);

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(addressRepository.findByPublicAddressId("ADDR-CUST")).thenReturn(Optional.of(deliveryAddress));

        OrderRequestDto request = pickupRequest()
                .fulfillmentType("DELIVERY").deliveryAddressPublicId("ADDR-CUST").build();

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(DeliveryOutOfRangeException.class);
    }

    @Test
    void createOrder_productNotBelongingToVendor_throwsIllegalState() {
        VendorProfile otherVendor = VendorProfile.builder().id(999L).build();
        product.setVendor(otherVendor);

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder(1L, pickupRequest().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to this vendor");
    }

    @Test
    void createOrder_productUnavailable_throwsIllegalState() {
        product.setAvailable(false);

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder(1L, pickupRequest().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void createOrder_advanceOrderMissingFulfillmentTime_throwsIllegalArgument() {
        product.setScheduleType(ScheduleType.ADVANCE_ORDER);
        product.setAdvanceNoticeHours(24);

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder(1L, pickupRequest().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("advance notice");
    }

    @Test
    void createOrder_advanceOrderInsufficientNotice_throwsIllegalArgument() {
        product.setScheduleType(ScheduleType.ADVANCE_ORDER);
        product.setAdvanceNoticeHours(24);

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));

        OrderRequestDto request = pickupRequest()
                .requestedFulfillmentTime(LocalDateTime.now().plusHours(1))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 24 hours");
    }

    @Test
    void createOrder_belowMinimumOrderAmount_throwsIllegalState() {
        vendor.setMinimumOrderAmount(new BigDecimal("100.00"));

        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder(1L, pickupRequest().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum amount");
    }

    @Test
    void createOrder_withPromoCode_appliesDiscountAndRecordsUsage() {
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(promotionService.calculateDiscount(eq("save5"), any(BigDecimal.class),
                eq("CUS123"), eq("VEN123"), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("5.00"));
        when(paymentService.chargeOrder(any(Order.class), eq("pm_123")))
                .thenReturn(new PaymentService.ChargeOutcome(PaymentStatus.AUTHORIZED, null, null));

        OrderRequestDto request = pickupRequest().promoCode("save5").build();

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        orderService.createOrder(1L, request);

        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getDiscount()).isEqualByComparingTo("5.00");
        assertThat(captor.getValue().getAppliedPromoCode()).isEqualTo("SAVE5");
        verify(promotionService).recordUsage(eq("save5"), eq(customerUser), any(Order.class), eq(new BigDecimal("5.00")));
    }

    @Test
    void createOrder_paymentRequiresAction_returnsPendingWithClientSecretAndSkipsOrderEvents() {
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(paymentService.chargeOrder(any(Order.class), eq("pm_123")))
                .thenReturn(new PaymentService.ChargeOutcome(PaymentStatus.PENDING, "pi_123_secret", null));

        OrderResponseDto response = orderService.createOrder(1L, pickupRequest().build());

        assertThat(response.getRequiresAction()).isTrue();
        assertThat(response.getStripeClientSecret()).isEqualTo("pi_123_secret");
        verify(outboxEventService, never()).orderPlaced(anyString());
        verify(outboxEventService, never()).customerOrderReceived(anyString());
    }

    @Test
    void createOrder_paymentDeclines_throwsIllegalStateWithoutDoublingMessage() {
        when(customerProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(customer));
        when(vendorProfileRepository.findByUser_PublicUserId("VEN123")).thenReturn(Optional.of(vendor));
        when(productRepository.findByPublicProductId("PROD-1")).thenReturn(Optional.of(product));
        when(paymentService.chargeOrder(any(Order.class), eq("pm_123")))
                .thenThrow(new RuntimeException("Payment failed: Your card was declined."));

        assertThatThrownBy(() -> orderService.createOrder(1L, pickupRequest().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment failed: Your card was declined.");
        verify(outboxEventService, never()).orderPlaced(anyString());
    }

    // ========== cancelCustomerOrder ==========

    @Test
    void cancelCustomerOrder_success_refundsAndCancelsWithoutVendorNotice() {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));

        orderService.cancelCustomerOrder(1L, "AFC-EXIST01");

        verify(paymentService).refundStripeCharge(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("CUSTOMER");
        verify(outboxEventService).orderCancelled(eq("AFC-EXIST01"), anyString(), eq("PENDING"), eq("CUSTOMER"));
        verify(outboxEventService, never()).vendorCustomerCancelled(anyString());
    }

    @Test
    void cancelCustomerOrder_vendorAlreadyAccepted_alsoNotifiesVendor() {
        Order order = sampleOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));

        orderService.cancelCustomerOrder(1L, "AFC-EXIST01");

        verify(outboxEventService).vendorCustomerCancelled("AFC-EXIST01");
    }

    @Test
    void cancelCustomerOrder_wrongCustomer_throwsIllegalStateAndSkipsRefund() {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelCustomerOrder(999L, "AFC-EXIST01"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).refundStripeCharge(any());
    }

    @Test
    void cancelCustomerOrder_pastCancellationWindow_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.CONFIRMED);
        order.setOrderTime(LocalDateTime.now().minusHours(10)); // window is 6h
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelCustomerOrder(1L, "AFC-EXIST01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hours");
        verify(paymentService, never()).refundStripeCharge(any());
    }

    // ========== adminCancelOrder ==========

    @Test
    void adminCancelOrder_success_refundsAndCancels() {
        Order order = sampleOrder(OrderStatus.PREPARING);
        when(orderRepository.findByPublicOrderId("AFC-EXIST01")).thenReturn(Optional.of(order));

        orderService.adminCancelOrder("AFC-EXIST01");

        verify(paymentService).refundStripeCharge(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("ADMIN");
    }

    @Test
    void adminCancelOrder_terminalState_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.DELIVERED);
        when(orderRepository.findByPublicOrderId("AFC-EXIST01")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.adminCancelOrder("AFC-EXIST01"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).refundStripeCharge(any());
    }

    // ========== acceptOrder ==========

    @Test
    void acceptOrder_success_computesDeadlineConfirmsAndCaptures() {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        orderService.acceptOrder("vendor1", "AFC-EXIST01");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getFulfillmentDeadline()).isNotNull(); // derived from prep time (SAME_DAY)
        verify(outboxEventService).orderConfirmed("AFC-EXIST01");
        verify(paymentService).captureStripePayment(order, null);
    }

    @Test
    void acceptOrder_wrongVendor_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.PENDING);
        VendorProfile otherVendor = VendorProfile.builder().id(777L).user(vendorUser).build();
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(otherVendor));

        assertThatThrownBy(() -> orderService.acceptOrder("vendor1", "AFC-EXIST01"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).captureStripePayment(any(), any());
    }

    @Test
    void acceptOrder_notPending_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> orderService.acceptOrder("vendor1", "AFC-EXIST01"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========== rejectOrder ==========

    @Test
    void rejectOrder_success_releasesHoldAndCancels() {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        orderService.rejectOrder("vendor1", "AFC-EXIST01");

        verify(paymentService).refundStripeCharge(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("VENDOR");
    }

    @Test
    void rejectOrder_notPending_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> orderService.rejectOrder("vendor1", "AFC-EXIST01"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).refundStripeCharge(any());
    }

    // ========== vendorUnableToFulfil ==========

    @Test
    void vendorUnableToFulfil_success_refundsCancelsAndFiresDedicatedEvent() {
        Order order = sampleOrder(OrderStatus.PREPARING);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        orderService.vendorUnableToFulfil("vendor1", "AFC-EXIST01", "Kitchen fire");

        verify(paymentService).refundStripeCharge(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("VENDOR_POST_ACCEPT");
        verify(outboxEventService).vendorUnableToFulfil("AFC-EXIST01", "Kitchen fire");
        verify(outboxEventService).orderCancelled(eq("AFC-EXIST01"), eq("Kitchen fire"), eq("PREPARING"), eq("VENDOR_POST_ACCEPT"));
    }

    @Test
    void vendorUnableToFulfil_wrongStatus_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.READY_FOR_PICKUP);
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> orderService.vendorUnableToFulfil("vendor1", "AFC-EXIST01", "reason"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentService, never()).refundStripeCharge(any());
    }

    // ========== commitOrderExpiry / autoExpireOrder ==========

    @Test
    void commitOrderExpiry_pending_marksCancelledAndReturnsTrue() {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        boolean proceeded = orderService.commitOrderExpiry(order);

        assertThat(proceeded).isTrue();
        verify(paymentService).markPaymentCancelled(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("SYSTEM");
        verify(outboxEventService).orderCancelled(eq("AFC-EXIST01"), anyString(), eq("PENDING"), eq("SYSTEM"));
    }

    @Test
    void commitOrderExpiry_alreadyHandled_returnsFalseNoOp() {
        Order order = sampleOrder(OrderStatus.CONFIRMED); // vendor already accepted before SLA fired
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        boolean proceeded = orderService.commitOrderExpiry(order);

        assertThat(proceeded).isFalse();
        verify(paymentService, never()).markPaymentCancelled(any());
        verify(outboxEventService, never()).orderCancelled(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void autoExpireOrder_proceeds_thenCancelsStripeAuthorizationOutsideTransaction() {
        Order order = sampleOrder(OrderStatus.PENDING);
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        orderService.autoExpireOrder(order);

        verify(paymentService).cancelStripeAuthorization(order);
    }

    // ========== markOrderDelivered / autoDeliverOrder ==========

    @Test
    void markOrderDelivered_pickupReady_marksDeliveredAndQueuesTransfer() {
        Order order = sampleOrder(OrderStatus.READY_FOR_PICKUP);
        order.setFulfillmentType("PICKUP");
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        orderService.markOrderDelivered("vendor1", "AFC-EXIST01", null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(vendorProfileRepository).incrementCompletedOrderStats(eq(5L), any(BigDecimal.class));
        verify(outboxEventService).paymentTransferRequested("AFC-EXIST01");
        verify(outboxEventService).orderDelivered("AFC-EXIST01");
    }

    @Test
    void markOrderDelivered_deliveryOrderNotYetOutForDelivery_throwsIllegalState() {
        Order order = sampleOrder(OrderStatus.READY_FOR_PICKUP);
        order.setFulfillmentType("DELIVERY"); // requires OUT_FOR_DELIVERY, not READY_FOR_PICKUP
        when(orderRepository.findByPublicOrderIdWithLock("AFC-EXIST01")).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("vendor1")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> orderService.markOrderDelivered("vendor1", "AFC-EXIST01", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void autoDeliverOrder_success_marksDeliveredAndFiresEvents() {
        Order order = sampleOrder(OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        orderService.autoDeliverOrder(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(vendorProfileRepository).incrementCompletedOrderStats(eq(5L), any(BigDecimal.class));
        verify(outboxEventService).orderDelivered("AFC-EXIST01");
    }

    // ========== flagOrderOverdue / autoCancelOverdueOrder ==========

    @Test
    void flagOrderOverdue_confirmedAndUnflagged_setsFlagAndNotifies() {
        Order order = sampleOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        orderService.flagOrderOverdue(order);

        assertThat(order.getOverdueFlaggedAt()).isNotNull();
        verify(outboxEventService).orderFulfillmentOverdue("AFC-EXIST01");
    }

    @Test
    void flagOrderOverdue_alreadyFlagged_noOp() {
        Order order = sampleOrder(OrderStatus.CONFIRMED);
        order.setOverdueFlaggedAt(LocalDateTime.now().minusMinutes(30));
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        orderService.flagOrderOverdue(order);

        verify(outboxEventService, never()).orderFulfillmentOverdue(anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void autoCancelOverdueOrder_stillUnresolved_refundsAndCancels() {
        Order order = sampleOrder(OrderStatus.PREPARING);
        order.setOverdueFlaggedAt(LocalDateTime.now().minusHours(1));
        when(orderRepository.findByOrderIdWithLock(500L)).thenReturn(Optional.of(order));

        orderService.autoCancelOverdueOrder(order);

        verify(paymentService).refundStripeCharge(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("SYSTEM_OVERDUE");
        verify(outboxEventService).orderCancelled(eq("AFC-EXIST01"), anyString(), eq("PREPARING"), eq("SYSTEM_OVERDUE"));
    }
}
