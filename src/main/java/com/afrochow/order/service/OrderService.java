package com.afrochow.order.service;

import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.Province;
import com.afrochow.common.enums.ProvincialTax;
import com.afrochow.common.enums.ScheduleType;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.outbox.service.OutboxEventService;
import com.afrochow.promotion.service.PromotionService;
import com.afrochow.order.dto.OrderRequestDto;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.orderline.dto.OrderLineRequestDto;
import com.afrochow.orderline.dto.OrderLineResponseDto;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import com.afrochow.payment.service.PaymentService;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    @Value("${order.sla.accept-window-minutes:10}")
    private int slaAcceptWindowMinutes;

    @Value("${order.cancellation.window-hours:6}")
    private int cancellationWindowHours;

    private final OrderRepository orderRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PromotionService promotionService;
    private final OutboxEventService outboxEventService;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            VendorProfileRepository vendorProfileRepository,
            AddressRepository addressRepository,
            ProductRepository productRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            PromotionService promotionService,
            OutboxEventService outboxEventService
    ) {
        this.orderRepository           = orderRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.vendorProfileRepository   = vendorProfileRepository;
        this.addressRepository         = addressRepository;
        this.productRepository         = productRepository;
        this.paymentRepository         = paymentRepository;
        this.userRepository            = userRepository;
        this.paymentService            = paymentService;
        this.promotionService          = promotionService;
        this.outboxEventService        = outboxEventService;
    }

    // ========== HELPER METHODS ==========

    private VendorProfile getVendorByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with username: " + username));
        return vendorProfileRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Vendor profile not found for user: " + username));
    }

    // ========== CUSTOMER METHODS ==========

    @Transactional
    public OrderResponseDto createOrder(Long customerUserId, OrderRequestDto request) {

        // ── Load and validate entities ────────────────────────────────────────

        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        if (!vendor.getIsActive()) {
            throw new IllegalStateException("Vendor is not active");
        }
        if (!vendor.getIsVerified()) {
            throw new IllegalStateException("Vendor is not verified");
        }

        // ── Resolve fulfillment type and delivery address ─────────────────────

        boolean isDelivery = "DELIVERY".equalsIgnoreCase(request.getFulfillmentType());

        Address deliveryAddress = null;
        String taxProvinceCode;

        if (isDelivery) {
            if (request.getDeliveryAddressPublicId() == null ||
                    request.getDeliveryAddressPublicId().isBlank()) {
                throw new IllegalArgumentException(
                        "deliveryAddressPublicId is required for DELIVERY orders");
            }

            deliveryAddress = addressRepository.findByPublicAddressId(
                            request.getDeliveryAddressPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Delivery address not found"));

            if (!deliveryAddress.getCustomerProfile().getCustomerProfileId()
                    .equals(customer.getCustomerProfileId())) {
                throw new IllegalStateException("Address does not belong to this customer");
            }

            // FIX 2: Address.getProvince() returns a Province enum — call .name() to get the code
            taxProvinceCode = deliveryAddress.getProvince().name();

        } else {
            // FIX 1: Removed invalid (Province) "AB" cast — plain String fallback
            // FIX 2: vendor.getAddress().getProvince() returns Province enum — call .name()
            taxProvinceCode = vendor.getAddress() != null
                    ? vendor.getAddress().getProvince().name()
                    : Province.AB.name();
        }

        // ── Resolve provincial tax ────────────────────────────────────────────

        ProvincialTax provincialTax = ProvincialTax.fromCode(taxProvinceCode);

        // ── Build order ───────────────────────────────────────────────────────

        Order order = new Order();
        order.setCustomer(customer);
        order.setVendor(vendor);
        order.setDeliveryAddress(deliveryAddress);
        order.setFulfillmentType(request.getFulfillmentType().toUpperCase());
        order.setSpecialInstructions(request.getSpecialInstructions());
        order.setDeliveryFee(isDelivery ? vendor.getDeliveryFee() : BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING);
        order.setTaxRate(provincialTax.getRate());
        order.setTaxLabel(provincialTax.getTaxLabel());
        order.setTaxProvince(taxProvinceCode);
        order.setRequestedFulfillmentTime(request.getRequestedFulfillmentTime());

        // ── Build order lines ─────────────────────────────────────────────────

        List<OrderLine> orderLines = new ArrayList<>();
        for (OrderLineRequestDto lineDto : request.getOrderLines()) {
            Product product = productRepository.findByPublicProductId(lineDto.getProductPublicId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product not found: " + lineDto.getProductPublicId()));

            if (!product.getVendor().getId().equals(vendor.getId())) {
                throw new IllegalStateException(
                        "Product " + product.getName() + " does not belong to this vendor");
            }

            if (!product.getAvailable()) {
                throw new IllegalStateException(
                        "Product " + product.getName() + " is not available");
            }

            // ── Advance-order validation ───────────────────────────────────
            if (product.getScheduleType() == ScheduleType.ADVANCE_ORDER) {
                if (request.getRequestedFulfillmentTime() == null) {
                    throw new IllegalArgumentException(
                            "'" + product.getName() + "' requires advance notice. " +
                            "Please provide a requestedFulfillmentTime.");
                }
                LocalDateTime now = LocalDateTime.now();
                // Compare in MINUTES (not HOURS) to avoid ChronoUnit.HOURS truncation:
                // e.g. 47h 59m would truncate to 47 hours and incorrectly fail a 48h requirement.
                long minutesUntil = ChronoUnit.MINUTES.between(now, request.getRequestedFulfillmentTime());
                int required = product.getAdvanceNoticeHours() != null ? product.getAdvanceNoticeHours() : 24;
                if (minutesUntil < (long) required * 60) {
                    throw new IllegalArgumentException(
                            "'" + product.getName() + "' requires at least " + required +
                            " hours advance notice. Please choose a later fulfilment time.");
                }
                // ── Stripe 7-day authorization window ─────────────────────
                // Stripe card authorizations expire after 7 days. We cap advance
                // orders at 6 days so capture at acceptance always succeeds.
                long daysUntil = ChronoUnit.DAYS.between(now, request.getRequestedFulfillmentTime());
                if (daysUntil > 6) {
                    throw new IllegalArgumentException(
                            "Orders cannot be scheduled more than 6 days in advance. " +
                            "Please choose a fulfilment time within the next 6 days.");
                }
            }

            OrderLine orderLine = OrderLine.builder()
                    .product(product)
                    .quantity(lineDto.getQuantity())
                    .priceAtPurchase(product.getPrice())
                    .productNameAtPurchase(product.getName())
                    .productDescriptionAtPurchase(product.getDescription())
                    .specialInstructions(lineDto.getSpecialInstructions())
                    .build();

            orderLine.setOrder(order);
            orderLines.add(orderLine);
        }

        order.setOrderLines(orderLines);

        // ── Validate minimum order amount ─────────────────────────────────────

        if (vendor.getMinimumOrderAmount() != null &&
                order.calculateSubtotal().compareTo(vendor.getMinimumOrderAmount()) < 0) {
            throw new IllegalStateException(
                    "Order does not meet minimum amount of $" + vendor.getMinimumOrderAmount());
        }

        // ── Apply promo code (before save so @PrePersist uses discounted total) ──

        BigDecimal promoDiscount = BigDecimal.ZERO;
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoDiscount = promotionService.calculateDiscount(
                    request.getPromoCode(),
                    order.calculateSubtotal(),
                    customer.getUser().getPublicUserId(),
                    request.getVendorPublicId()
            );
            order.setDiscount(promoDiscount);
            order.setAppliedPromoCode(request.getPromoCode().toUpperCase().trim());
        }

        // ── Save order ────────────────────────────────────────────────────────

        Order savedOrder = orderRepository.save(order);
        log.info("order.created publicOrderId={} customerUserId={} vendorPublicId={} totalAmount={} status={}",
                savedOrder.getPublicOrderId(),
                customerUserId,
                request.getVendorPublicId(),
                savedOrder.getTotalAmount(),
                savedOrder.getStatus());

        // ── Create pending payment record ─────────────────────────────────────

        Payment payment = Payment.builder()
                .order(savedOrder)
                .amount(savedOrder.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
        paymentRepository.save(payment);
        log.info("payment.record.created publicOrderId={} paymentId={} paymentStatus={}",
                savedOrder.getPublicOrderId(),
                payment.getPaymentId(),
                payment.getStatus());

        // ── Charge via Stripe ─────────────────────────────────────────────────

        try {
            paymentService.chargeOrder(savedOrder, request.getPaymentMethodId());
        } catch (RuntimeException e) {
            log.warn("payment.charge.failed publicOrderId={} message={}",
                    savedOrder.getPublicOrderId(),
                    e.getMessage());
            throw new IllegalStateException("Payment failed: " + e.getMessage());
        }

        // ── Payment succeeded — order stays PENDING until vendor manually accepts ──────

        // Record promo usage now that payment is confirmed
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()
                && promoDiscount.compareTo(BigDecimal.ZERO) > 0) {
            promotionService.recordUsage(
                    request.getPromoCode(),
                    customer.getUser(),
                    savedOrder,
                    promoDiscount
            );
        }

        outboxEventService.orderPlaced(savedOrder.getPublicOrderId());
        outboxEventService.customerOrderReceived(savedOrder.getPublicOrderId());

        return toResponseDto(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getCustomerOrders(Long customerUserId) {
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));
        return orderRepository.findByCustomerOrderByOrderTimeDesc(customer).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getCustomerActiveOrders(Long customerUserId) {
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));
        return orderRepository.findActiveOrdersByCustomer(customer).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getCustomerOrder(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only view your own orders");
        }

        return toResponseDto(order);
    }

    @Transactional
    public OrderResponseDto cancelCustomerOrder(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only cancel your own orders");
        }

        if (!order.canBeCancelled(cancellationWindowHours)) {
            boolean pastWindow = order.getOrderTime() != null &&
                    !LocalDateTime.now().isBefore(order.getOrderTime().plusHours(cancellationWindowHours));
            if (pastWindow) {
                throw new IllegalStateException(
                        "Orders can only be cancelled within " + cancellationWindowHours
                        + " hours of placement. Please contact Afrochow support for assistance.");
            }
            throw new IllegalStateException(
                    "This order can no longer be cancelled. Please contact Afrochow support.");
        }

        boolean vendorAlreadyAccepted = order.getStatus() == OrderStatus.CONFIRMED;
        String previousStatus = order.getStatus().toString();
        paymentService.refundStripeCharge(order);
        order.updateStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        log.info("order.cancelled publicOrderId={} actor=customer customerUserId={} fromStatus={} toStatus={}",
                updatedOrder.getPublicOrderId(),
                customerUserId,
                previousStatus,
                updatedOrder.getStatus());
        outboxEventService.orderCancelled(updatedOrder.getPublicOrderId(), "Cancelled by customer", previousStatus, "CUSTOMER");

        // If the vendor had already accepted, notify them to stop any prep work
        if (vendorAlreadyAccepted) {
            outboxEventService.vendorCustomerCancelled(updatedOrder.getPublicOrderId());
        }

        return toResponseDto(updatedOrder);
    }

    @Transactional
    public OrderResponseDto adminCancelOrder(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        OrderStatus status = order.getStatus();
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            throw new IllegalStateException("Order is already in a terminal state and cannot be cancelled");
        }

        String previousStatus = status.toString();
        paymentService.refundStripeCharge(order);
        order.updateStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        log.info("order.cancelled publicOrderId={} actor=admin fromStatus={} toStatus={}",
                updatedOrder.getPublicOrderId(),
                previousStatus,
                updatedOrder.getStatus());
        outboxEventService.orderCancelled(updatedOrder.getPublicOrderId(), "Cancelled by admin", previousStatus, "ADMIN");

        return toResponseDto(updatedOrder);
    }

    // ========== VENDOR METHODS ==========

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getVendorOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);
        return orderRepository.findByVendorOrderByOrderTimeDesc(vendor).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getVendorActiveOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);
        return orderRepository.findActiveOrdersByVendor(vendor).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getVendorTodayOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);
        return orderRepository.findTodayOrdersByVendor(vendor).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getVendorOrdersByStatus(String username, OrderStatus status) {
        VendorProfile vendor = getVendorByUsername(username);
        return orderRepository.findByVendorAndStatus(vendor, status).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getVendorOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only view orders for your restaurant");
        }
        return toResponseDto(order);
    }

    @Transactional
    public OrderResponseDto acceptOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only accept orders for your restaurant");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be accepted");
        }

        order.updateStatus(OrderStatus.CONFIRMED);
        Order updatedOrder = orderRepository.save(order);
        log.info("order.accepted publicOrderId={} actor=vendor username={} fromStatus={} toStatus={}",
                updatedOrder.getPublicOrderId(),
                username,
                OrderStatus.PENDING,
                updatedOrder.getStatus());

        // Capture the previously-authorised payment now that the vendor has confirmed.
        // If capture fails, @Transactional rolls back the status update — order stays PENDING.
        paymentService.captureStripePayment(updatedOrder, null);

        outboxEventService.orderConfirmed(updatedOrder.getPublicOrderId());
        return toResponseDto(updatedOrder);
    }

    @Transactional
    public OrderResponseDto rejectOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only reject orders for your restaurant");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be rejected");
        }
        String previousStatus = order.getStatus().toString();
        paymentService.refundStripeCharge(order);
        order.updateStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        log.info("order.rejected publicOrderId={} actor=vendor username={} fromStatus={} toStatus={}",
                updatedOrder.getPublicOrderId(),
                username,
                previousStatus,
                updatedOrder.getStatus());
        outboxEventService.orderCancelled(updatedOrder.getPublicOrderId(), "Rejected by vendor", previousStatus, "VENDOR");
        return toResponseDto(updatedOrder);
    }

    /**
     * Called by {@link com.afrochow.order.service.OrderSlaService} when a PENDING order
     * exceeds the vendor acceptance window.  Uses the same refund + cancel path as
     * {@link #rejectOrder} but does not require a vendor principal — the scheduler acts
     * as the system actor.
     */
    @Transactional
    public void autoExpireOrder(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) return; // guard: job may race
        paymentService.refundStripeCharge(order);
        order.updateStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        outboxEventService.orderCancelled(
                order.getPublicOrderId(),
                "Vendor did not respond in time — your order has been automatically cancelled and refunded.",
                "PENDING",
                "SYSTEM"
        );
    }

    /**
     * Called by {@link com.afrochow.order.service.FulfillmentSafetyNetScheduler}
     * when an order is still OUT_FOR_DELIVERY or READY_FOR_PICKUP past the 2-hour
     * grace period. Marks the order as DELIVERED and captures payment.
     * The scheduler acts as the system actor — no vendor principal required.
     */
    @Transactional
    public void autoDeliverOrder(Order order) {
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY &&
            order.getStatus() != OrderStatus.READY_FOR_PICKUP) return; // guard: scheduler may race
        order.updateStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        // Payment was captured at acceptance (CONFIRMED).  Now that the order is
        // delivered, pay out the vendor's share from the platform account.
        paymentService.transferToVendor(order);
        VendorProfile vendor = order.getVendor();
        vendor.recordCompletedOrder(order.getTotalAmount());
        vendorProfileRepository.save(vendor);
        outboxEventService.orderDelivered(order.getPublicOrderId());
    }

    /**
     * Called by {@link com.afrochow.order.service.FulfillmentSafetyNetScheduler}
     * to retry the vendor payout Transfer for an order that was marked DELIVERED
     * but whose transfer failed due to a transient error.
     *
     * Under the transfer_group model capture happens at acceptance (CONFIRMED), not
     * at delivery.  So the retry here is for the Transfer, not the capture.
     * transferToVendor() is idempotent — it no-ops if the Transfer ID is already stored.
     */
    @Transactional
    public void retryCaptureForDeliveredOrder(Order order) {
        paymentService.transferToVendor(order);
    }

    @Transactional
    public OrderResponseDto startPreparingOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only prepare orders for your restaurant");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed orders can be prepared");
        }
        order.updateStatus(OrderStatus.PREPARING);
        Order updatedOrder = orderRepository.save(order);
        outboxEventService.orderPreparing(updatedOrder.getPublicOrderId());
        return toResponseDto(updatedOrder);
    }

    @Transactional
    public OrderResponseDto markOrderReady(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only mark orders ready for your restaurant");
        }
        if (order.getStatus() != OrderStatus.PREPARING) {
            throw new IllegalStateException("Only preparing orders can be marked as ready");
        }
        order.updateStatus(OrderStatus.READY_FOR_PICKUP);
        Order updatedOrder = orderRepository.save(order);
        outboxEventService.orderReady(updatedOrder.getPublicOrderId());
        return toResponseDto(updatedOrder);
    }

    @Transactional
    public OrderResponseDto markOrderOutForDelivery(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException(
                    "You can only mark orders for delivery for your restaurant");
        }
        if ("PICKUP".equalsIgnoreCase(order.getFulfillmentType())) {
            throw new IllegalStateException("Pickup orders do not go out for delivery");
        }
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException("Only ready orders can be sent out for delivery");
        }
        order.updateStatus(OrderStatus.OUT_FOR_DELIVERY);
        Order updatedOrder = orderRepository.save(order);
        outboxEventService.orderOutForDelivery(updatedOrder.getPublicOrderId());
        return toResponseDto(updatedOrder);
    }

    /**
     * Mark an order as delivered and capture the Stripe payment.
     *
     * @param username      authenticated vendor's username
     * @param publicOrderId order reference
     * @param finalAmount   optional — if provided, captures this amount instead of the
     *                      full authorization (e.g. an item was substituted or removed).
     *                      Must be > 0 and ≤ the originally authorized amount.
     *                      Pass null to capture the full amount.
     */
    @Transactional
    public OrderResponseDto markOrderDelivered(String username, String publicOrderId, java.math.BigDecimal finalAmount) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        VendorProfile vendor = getVendorByUsername(username);
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException(
                    "You can only mark orders delivered for your restaurant");
        }
        boolean isPickup = "PICKUP".equalsIgnoreCase(order.getFulfillmentType());
        OrderStatus requiredStatus = isPickup ? OrderStatus.READY_FOR_PICKUP : OrderStatus.OUT_FOR_DELIVERY;
        if (order.getStatus() != requiredStatus) {
            throw new IllegalStateException(isPickup
                    ? "Only orders available for pickup can be marked as picked up"
                    : "Only out-for-delivery orders can be marked as delivered");
        }
        order.updateStatus(OrderStatus.DELIVERED);
        Order updatedOrder = orderRepository.save(order);
        // Payment was captured at acceptance (CONFIRMED).  Now that delivery is confirmed,
        // move the vendor's share from the platform account to their Stripe connected account.
        // Do NOT call captureStripePayment here — capture already happened at acceptance.
        paymentService.transferToVendor(updatedOrder);
        vendor.recordCompletedOrder(updatedOrder.getTotalAmount());
        vendorProfileRepository.save(vendor);
        outboxEventService.orderDelivered(updatedOrder.getPublicOrderId());
        return toResponseDto(updatedOrder);
    }

    // ========== ADMIN METHODS ==========

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        return toResponseDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getActiveOrders() {
        return orderRepository.findActiveOrders().stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponseDto> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByOrderTimeDesc(status).stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    // ========== STATISTICS ==========

    public Long countCustomerOrders(Long customerUserId) {
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));
        return orderRepository.countByCustomer(customer);
    }

    public Long countVendorOrders(String username) {
        return orderRepository.countByVendor(getVendorByUsername(username));
    }

    public BigDecimal getVendorRevenue(String username) {
        BigDecimal revenue = orderRepository.calculateVendorRevenue(getVendorByUsername(username));
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    public BigDecimal getVendorTodayRevenue(String username) {
        BigDecimal revenue = orderRepository.calculateVendorTodayRevenue(getVendorByUsername(username));
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // ========== MAPPING ==========

    private OrderResponseDto toResponseDto(Order order) {
        if (order == null) return null;

        return OrderResponseDto.builder()
                .publicOrderId(order.getPublicOrderId())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .appliedPromoCode(order.getAppliedPromoCode())
                .totalAmount(order.getTotalAmount())
                .fulfillmentType(order.getFulfillmentType())
                .requestedFulfillmentTime(order.getRequestedFulfillmentTime())
                .status(order.getStatus())
                .statusLabel(resolveStatusLabel(order.getStatus(), order.getFulfillmentType()))
                .specialInstructions(order.getSpecialInstructions())
                .customerPublicId(getCustomerPublicId(order))
                .customerName(getCustomerFullName(order))
                .vendorPublicId(order.getVendor() != null
                        ? order.getVendor().getPublicVendorId() : null)
                .vendorName(order.getVendor() != null
                        ? order.getVendor().getRestaurantName() : null)
                .restaurantName(order.getVendor() != null
                        ? order.getVendor().getRestaurantName() : null)
                .deliveryAddress(toAddressResponseDto(order.getDeliveryAddress()))
                .orderLines(order.getOrderLines() != null
                        ? order.getOrderLines().stream()
                        .map(this::toOrderLineResponseDto).toList()
                        : List.of())
                .payment(toPaymentResponseDto(order.getPayment()))
                .orderTime(order.getOrderTime())
                .createdAt(order.getCreatedAt())
                .confirmedAt(order.getConfirmedAt())
                .preparingAt(order.getPreparingAt())
                .readyAt(order.getReadyAt())
                .outForDeliveryAt(order.getOutForDeliveryAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime())
                .updatedAt(order.getUpdatedAt())
                // FIX 4: Delegate to Order.canBeCancelled() — single source of truth
                .canBeCancelled(order.canBeCancelled())
                .isCompleted(order.getStatus() != null
                        && order.getStatus() == OrderStatus.DELIVERED)
                .isActive(order.getStatus() != null
                        && order.getStatus() != OrderStatus.CANCELLED)
                .slaExpiresAt(order.getStatus() == OrderStatus.PENDING && order.getOrderTime() != null
                        ? order.getOrderTime().plusMinutes(slaAcceptWindowMinutes) : null)
                .slaRemainingSeconds(order.getStatus() == OrderStatus.PENDING && order.getOrderTime() != null
                        ? ChronoUnit.SECONDS.between(LocalDateTime.now(),
                                order.getOrderTime().plusMinutes(slaAcceptWindowMinutes)) : null)
                .build();
    }

    private String getCustomerPublicId(Order order) {
        if (order.getCustomer() != null && order.getCustomer().getUser() != null) {
            return order.getCustomer().getUser().getPublicUserId();
        }
        return null;
    }

    private String getCustomerFullName(Order order) {
        if (order.getCustomer() != null && order.getCustomer().getUser() != null) {
            return order.getCustomer().getUser().getFirstName() + " "
                    + order.getCustomer().getUser().getLastName();
        }
        return null;
    }

    private OrderSummaryResponseDto toSummaryResponseDto(Order order) {
        List<String> itemNames = order.getOrderLines() == null ? List.of()
                : order.getOrderLines().stream()
                        .map(line -> line.getProductNameAtPurchase() != null
                                ? line.getProductNameAtPurchase()
                                : (line.getProduct() != null ? line.getProduct().getName() : "Unknown item"))
                        .toList();

        return OrderSummaryResponseDto.builder()
                .publicOrderId(order.getPublicOrderId())
                .vendorPublicId(order.getVendor() != null
                        ? order.getVendor().getUser().getPublicUserId() : null)
                .vendorName(order.getVendor() != null
                        ? order.getVendor().getRestaurantName() : null)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .statusLabel(resolveStatusLabel(order.getStatus(), order.getFulfillmentType()))
                .orderTime(order.getOrderTime())
                .fulfillmentType(order.getFulfillmentType())
                .requestedFulfillmentTime(order.getRequestedFulfillmentTime())
                .canBeCancelled(order.canBeCancelled())
                .itemCount(itemNames.size())
                .itemNames(itemNames)
                .build();
    }

    private OrderLineResponseDto toOrderLineResponseDto(OrderLine orderLine) {
        return OrderLineResponseDto.builder()
                .orderLineId(orderLine.getOrderLineId())
                .productPublicId(orderLine.getProduct() != null
                        ? orderLine.getProduct().getPublicProductId() : null)
                .productNameAtPurchase(orderLine.getProductNameAtPurchase())
                .quantity(orderLine.getQuantity())
                .priceAtPurchase(orderLine.getPriceAtPurchase())
                .lineTotal(orderLine.getLineTotal())
                .specialInstructions(orderLine.getSpecialInstructions())
                .build();
    }

    private AddressResponseDto toAddressResponseDto(Address address) {
        if (address == null) return null;
        return AddressResponseDto.builder()
                .publicAddressId(address.getPublicAddressId())
                .addressLine(address.getAddressLine())
                .city(address.getCity())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .formattedAddress(address.getFormattedAddress())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .defaultAddress(address.getDefaultAddress())
                .build();
    }

    private PaymentResponseDto toPaymentResponseDto(Payment payment) {
        if (payment == null) return null;
        return PaymentResponseDto.builder()
                .publicOrderId(payment.getOrder() != null ? payment.getOrder().getPublicOrderId() : null)
                .amount(payment.getAmount())
                .platformFeeAmount(payment.getPlatformFeeAmount())
                .vendorPayout(payment.getVendorPayout())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .maskedCardNumber(payment.getMaskedCardNumber())
                .cardBrand(payment.getCardBrand())
                .notes(payment.getNotes())
                .isSuccessful(payment.getStatus() == PaymentStatus.COMPLETED)
                .isPending(payment.getStatus() == PaymentStatus.PENDING)
                .isFailed(payment.getStatus() == PaymentStatus.FAILED)
                .isRefunded(payment.getStatus() == PaymentStatus.REFUNDED)
                .paymentTime(payment.getPaymentTime())
                .completedAt(payment.getCompletedAt())
                .failedAt(payment.getFailedAt())
                .refundedAt(payment.getRefundedAt())
                .build();
    }

    private String resolveStatusLabel(OrderStatus status, String fulfillmentType) {
        if (status == null) return null;
        boolean isPickup = "PICKUP".equalsIgnoreCase(fulfillmentType);
        return switch (status) {
            case PENDING           -> "Awaiting Confirmation";
            case CONFIRMED         -> "Order Confirmed";
            case PREPARING         -> "Being Prepared";
            case READY_FOR_PICKUP  -> isPickup ? "Available for Pickup" : "Ready for Delivery";
            case OUT_FOR_DELIVERY  -> "Out for Delivery";
            case DELIVERED         -> isPickup ? "Picked Up" : "Delivered";
            case CANCELLED         -> "Cancelled";
            case REFUNDED          -> "Refunded";
        };
    }
}