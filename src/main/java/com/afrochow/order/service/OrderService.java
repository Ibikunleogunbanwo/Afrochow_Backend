package com.afrochow.order.service;

import com.afrochow.address.dto.AddressResponseDto;
import com.afrochow.address.model.Address;
import com.afrochow.address.repository.AddressRepository;
import com.afrochow.customer.model.CustomerProfile;
import com.afrochow.customer.repository.CustomerProfileRepository;
import com.afrochow.order.dto.OrderResponseDto;
import com.afrochow.order.dto.OrderSummaryResponseDto;
import com.afrochow.orderline.dto.OrderLineRequestDto;
import com.afrochow.order.dto.OrderRequestDto;
import com.afrochow.common.enums.OrderStatus;
import com.afrochow.common.enums.PaymentMethod;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.order.model.Order;
import com.afrochow.order.repository.OrderRepository;
import com.afrochow.orderline.dto.OrderLineResponseDto;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.payment.dto.PaymentResponseDto;
import com.afrochow.payment.model.Payment;
import com.afrochow.payment.repository.PaymentRepository;
import com.afrochow.product.model.Product;
import com.afrochow.product.repository.ProductRepository;
import com.afrochow.notification.service.NotificationService;
import com.afrochow.user.model.User;
import com.afrochow.user.repository.UserRepository;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing orders
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            VendorProfileRepository vendorProfileRepository,
            AddressRepository addressRepository,
            ProductRepository productRepository,
            PaymentRepository paymentRepository,
            NotificationService notificationService
    ) {
        this.orderRepository = orderRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // ========== HELPER METHODS ==========

    /**
     * Get vendor profile by username
     */
    private VendorProfile getVendorByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));

        return vendorProfileRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found for user: " + username));
    }

    // ========== CUSTOMER METHODS ==========

    /**
     * Create a new order (customer only)
     */
    @Transactional
    public OrderResponseDto createOrder(Long customerUserId, OrderRequestDto request) {
        // Get customer profile
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        // Get vendor
        VendorProfile vendor = vendorProfileRepository.findByUser_PublicUserId(request.getVendorPublicId())
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found"));

        // Validate vendor is active and verified
        if (!vendor.getIsActive()) {
            throw new IllegalStateException("Vendor is not active");
        }
        if (!vendor.getIsVerified()) {
            throw new IllegalStateException("Vendor is not verified");
        }

        // Get delivery address
        Address deliveryAddress = addressRepository.findByPublicAddressId(request.getDeliveryAddressPublicId())
                .orElseThrow(() -> new EntityNotFoundException("Delivery address not found"));

        // Verify address belongs to customer
        if (!deliveryAddress.getCustomerProfile().getCustomerProfileId().equals(customer.getCustomerProfileId())) {
            throw new IllegalStateException("Address does not belong to this customer");
        }

        // Create order
        Order order = new Order();
        order.setCustomer(customer);
        order.setVendor(vendor);
        order.setDeliveryAddress(deliveryAddress);
        order.setSpecialInstructions(request.getSpecialInstructions());
        order.setDeliveryFee(vendor.getDeliveryFee());
        order.setStatus(OrderStatus.PENDING);

        // Create order lines
        List<OrderLine> orderLines = new ArrayList<>();
        for (OrderLineRequestDto lineDto : request.getOrderLines()) {
            Product product = productRepository.findByPublicProductId(lineDto.getProductPublicId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + lineDto.getProductPublicId()));

            // Validate product belongs to the vendor
            if (!product.getVendor().getId().equals(vendor.getId())) {
                throw new IllegalStateException("Product " + product.getName() + " does not belong to this vendor");
            }

            // Validate product is available
            if (!product.getAvailable()) {
                throw new IllegalStateException("Product " + product.getName() + " is not available");
            }

            // Create order line
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

        // Check the minimum order amount
        if (order.getSubtotal().compareTo(vendor.getMinimumOrderAmount()) < 0) {
            throw new IllegalStateException("Order does not meet minimum amount of $" + vendor.getMinimumOrderAmount());
        }

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Create pending payment
        Payment payment = Payment.builder()
                .order(savedOrder)
                .amount(savedOrder.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();
        paymentRepository.save(payment);

        // Note: Order confirmation and vendor notification will be sent
        // after payment is successful (when order status changes to CONFIRMED)
        // For now, order is PENDING awaiting payment

        return toResponseDto(savedOrder);
    }

    /**
     * Get all orders for a customer
     */
    public List<OrderSummaryResponseDto> getCustomerOrders(Long customerUserId) {
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        List<Order> orders = orderRepository.findByCustomerOrderByOrderTimeDesc(customer);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get active orders for a customer
     */
    public List<OrderSummaryResponseDto> getCustomerActiveOrders(Long customerUserId) {
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));

        List<Order> orders = orderRepository.findActiveOrdersByCustomer(customer);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get order details (customer ownership check)
     */
    public OrderResponseDto getCustomerOrder(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // Verify ownership
        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only view your own orders");
        }

        return toResponseDto(order);
    }

    /**
     * Cancel order (customer only)
     */
    @Transactional
    public OrderResponseDto cancelCustomerOrder(Long customerUserId, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // Verify ownership
        if (!order.getCustomer().getUser().getUserId().equals(customerUserId)) {
            throw new IllegalStateException("You can only cancel your own orders");
        }

        // Validate order can be canceled
        if (!order.canBeCancelled()) {
            throw new IllegalStateException("Order cannot be cancelled at this stage");
        }

        // Update status
        order.updateStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        // Send cancellation notifications (in-app + email)
        notificationService.notifyCustomerOrderCancelled(updatedOrder, "Cancelled by customer");

        return toResponseDto(updatedOrder);
    }

    // ========== VENDOR METHODS ==========

    /**
     * Get all orders for a vendor
     */
    public List<OrderSummaryResponseDto> getVendorOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);

        List<Order> orders = orderRepository.findByVendorOrderByOrderTimeDesc(vendor);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get active orders for a vendor
     */
    public List<OrderSummaryResponseDto> getVendorActiveOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);

        List<Order> orders = orderRepository.findActiveOrdersByVendor(vendor);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get today's orders for a vendor
     */
    public List<OrderSummaryResponseDto> getVendorTodayOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);

        List<Order> orders = orderRepository.findTodayOrdersByVendor(vendor);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get orders by status for a vendor
     */
    public List<OrderSummaryResponseDto> getVendorOrdersByStatus(String username, OrderStatus status) {
        VendorProfile vendor = getVendorByUsername(username);

        List<Order> orders = orderRepository.findByVendorAndStatus(vendor, status);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get order details (vendor ownership check)
     */
    public OrderResponseDto getVendorOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only view orders for your restaurant");
        }

        return toResponseDto(order);
    }

    /**
     * Accept order (vendor only)
     */
    @Transactional
    public OrderResponseDto acceptOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only accept orders for your restaurant");
        }

        // Validate current status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be accepted");
        }

        // Update status
        order.updateStatus(OrderStatus.CONFIRMED);
        Order updatedOrder = orderRepository.save(order);

        // Send multi-channel notifications (in-app + email)
        notificationService.notifyCustomerOrderConfirmed(updatedOrder);
        notificationService.notifyVendorNewOrder(updatedOrder);

        return toResponseDto(updatedOrder);
    }

    /**
     * Reject order (vendor only)
     */
    @Transactional
    public OrderResponseDto rejectOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only reject orders for your restaurant");
        }

        // Validate current status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be rejected");
        }

        // Update status
        order.updateStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        // Send cancellation notifications (in-app + email)
        notificationService.notifyCustomerOrderCancelled(updatedOrder, "Rejected by vendor");

        return toResponseDto(updatedOrder);
    }

    /**
     * Start preparing order (vendor only)
     */
    @Transactional
    public OrderResponseDto startPreparingOrder(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only prepare orders for your restaurant");
        }

        // Validate current status
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed orders can be prepared");
        }

        // Update status
        order.updateStatus(OrderStatus.PREPARING);
        Order updatedOrder = orderRepository.save(order);

        // Send notification (in-app only - not critical enough for email)
        notificationService.notifyCustomerOrderPreparing(updatedOrder);

        return toResponseDto(updatedOrder);
    }

    /**
     * Mark order as ready (vendor only)
     */
    @Transactional
    public OrderResponseDto markOrderReady(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only mark orders ready for your restaurant");
        }

        // Validate current status
        if (order.getStatus() != OrderStatus.PREPARING) {
            throw new IllegalStateException("Only preparing orders can be marked as ready");
        }

        // Update status
        order.updateStatus(OrderStatus.READY_FOR_PICKUP);
        Order updatedOrder = orderRepository.save(order);

        // Send notifications (in-app + email)
        notificationService.notifyCustomerOrderReady(updatedOrder);

        return toResponseDto(updatedOrder);
    }

    /**
     * Mark order out for delivery (vendor only)
     */
    @Transactional
    public OrderResponseDto markOrderOutForDelivery(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only mark orders for delivery for your restaurant");
        }

        // Validate current status
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException("Only ready orders can be sent out for delivery");
        }

        // Update status
        order.updateStatus(OrderStatus.OUT_FOR_DELIVERY);
        Order updatedOrder = orderRepository.save(order);

        // Send notification (in-app only for real-time update)
        notificationService.notifyCustomerOrderOutForDelivery(updatedOrder);

        return toResponseDto(updatedOrder);
    }

    /**
     * Mark order as delivered (vendor only)
     */
    @Transactional
    public OrderResponseDto markOrderDelivered(String username, String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        VendorProfile vendor = getVendorByUsername(username);

        // Verify ownership
        if (!order.getVendor().getId().equals(vendor.getId())) {
            throw new IllegalStateException("You can only mark orders delivered for your restaurant");
        }

        // Validate current status
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Only out-for-delivery orders can be marked as delivered");
        }

        // Update status
        order.updateStatus(OrderStatus.DELIVERED);
        Order updatedOrder = orderRepository.save(order);

        // Update vendor statistics
        vendor.recordCompletedOrder(order.getTotalAmount());
        vendorProfileRepository.save(vendor);

        // Send notifications (in-app + email)
        notificationService.notifyCustomerOrderDelivered(updatedOrder);

        return toResponseDto(updatedOrder);
    }

    // ========== ADMIN METHODS ==========

    /**
     * Get all orders (admin only)
     */
    public List<OrderSummaryResponseDto> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get order by ID (admin only)
     */
    public OrderResponseDto getOrderById(String publicOrderId) {
        Order order = orderRepository.findByPublicOrderId(publicOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        return toResponseDto(order);
    }

    /**
     * Get active orders (admin only)
     */
    public List<OrderSummaryResponseDto> getActiveOrders() {
        List<Order> orders = orderRepository.findActiveOrders();
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get orders by status (admin only)
     */
    public List<OrderSummaryResponseDto> getOrdersByStatus(OrderStatus status) {
        List<Order> orders = orderRepository.findByStatusOrderByOrderTimeDesc(status);
        return orders.stream()
                .map(this::toSummaryResponseDto)
                .collect(Collectors.toList());
    }

    // ========== STATISTICS ==========

    /**
     * Count customer orders
     */
    public Long countCustomerOrders(Long customerUserId) {
        CustomerProfile customer = customerProfileRepository.findByUser_UserId(customerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Customer profile not found"));
        return orderRepository.countByCustomer(customer);
    }

    /**
     * Count vendor orders
     */
    public Long countVendorOrders(String username) {
        VendorProfile vendor = getVendorByUsername(username);
        return orderRepository.countByVendor(vendor);
    }

    /**
     * Get vendor revenue
     */
    public BigDecimal getVendorRevenue(String username) {
        VendorProfile vendor = getVendorByUsername(username);
        BigDecimal revenue = orderRepository.calculateVendorRevenue(vendor);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    /**
     * Get vendor today's revenue
     */
    public BigDecimal getVendorTodayRevenue(String username) {
        VendorProfile vendor = getVendorByUsername(username);
        BigDecimal revenue = orderRepository.calculateVendorTodayRevenue(vendor);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // ========== MAPPING METHODS ==========

    private OrderResponseDto toResponseDto(Order order) {
        if (order == null) return null;

        return OrderResponseDto.builder()
                // Order identification & financials
                .publicOrderId(order.getPublicOrderId())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .totalAmount(order.getTotalAmount())

                // Status & instructions
                .status(order.getStatus())
                .specialInstructions(order.getSpecialInstructions())

                // Customer info
                .customerPublicId(getCustomerPublicId(order))
                .customerName(getCustomerFullName(order))

                // Vendor info
                .vendorPublicId(order.getVendor() != null ? order.getVendor().getPublicVendorId() : null)
                .vendorName(order.getVendor() != null ? order.getVendor().getRestaurantName() : null)
                .restaurantName(order.getVendor() != null ? order.getVendor().getRestaurantName() : null)

                // Delivery address
                .deliveryAddress(toAddressResponseDto(order.getDeliveryAddress()))

                // Order lines
                .orderLines(order.getOrderLines() != null
                        ? order.getOrderLines().stream()
                        .map(this::toOrderLineResponseDto)
                        .toList()
                        : List.of())

                // Payment
                .payment(toPaymentResponseDto(order.getPayment()))

                // Timestamps
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

                // Flags
                .canBeCancelled(order.getStatus() != null && order.getStatus() == OrderStatus.PENDING)
                .isCompleted(order.getStatus() != null && order.getStatus() == OrderStatus.DELIVERED)
                .isActive(order.getStatus() != null && order.getStatus() != OrderStatus.CANCELLED)

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
            return order.getCustomer().getUser().getFirstName() + " " +
                    order.getCustomer().getUser().getLastName();
        }
        return null;
    }

    private OrderSummaryResponseDto toSummaryResponseDto(Order order) {
        return OrderSummaryResponseDto.builder()
                .publicOrderId(order.getPublicOrderId())
                .vendorName(order.getVendor() != null ? order.getVendor().getRestaurantName() : null)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus() != null ? order.getStatus() : null)
                .orderTime(order.getOrderTime())
                .build();
    }

    private OrderLineResponseDto toOrderLineResponseDto(OrderLine orderLine) {
        return OrderLineResponseDto.builder()
                .orderLineId(orderLine.getOrderLineId())
                .productPublicId(orderLine.getProduct() != null ? orderLine.getProduct().getPublicProductId() : null)
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
                .publicOrderId(payment.publicOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .maskedCardNumber(payment.getMaskedCardNumber())
                .cardBrand(payment.getCardBrand())
                .notes(payment.getNotes())

                // Flags
                .isSuccessful(payment.getStatus() == PaymentStatus.COMPLETED)
                .isPending(payment.getStatus() == PaymentStatus.PENDING)
                .isFailed(payment.getStatus() == PaymentStatus.FAILED)
                .isRefunded(payment.getStatus() == PaymentStatus.REFUNDED)

                // Timestamps
                .paymentTime(payment.getPaymentTime())
                .completedAt(payment.getCompletedAt())
                .failedAt(payment.getFailedAt())
                .refundedAt(payment.getRefundedAt())
                .build();
    }
}