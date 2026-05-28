package com.example.skishop.sales.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.common.security.SecurityUtils;
import com.example.skishop.sales.client.InventoryClient;
import com.example.skishop.sales.dto.*;
import com.example.skishop.sales.dto.OrderResponse.OrderItemResponse;
import com.example.skishop.sales.model.*;
import com.example.skishop.sales.repository.OrderRepository;
import com.example.skishop.sales.repository.ReturnRequestRepository;
import com.example.skishop.sales.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");
    private static final String PAYMENT_FAILED_RELEASE_REASON = "PAYMENT_FAILED";
    private final AtomicLong orderSequence = new AtomicLong(System.currentTimeMillis());

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final InventoryClient inventoryClient;
    private final EventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        ShipmentRepository shipmentRepository,
                        ReturnRequestRepository returnRequestRepository,
                        InventoryClient inventoryClient,
                        EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.customerId());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (var item : request.items()) {
            subtotal = subtotal.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }

        BigDecimal tax = subtotal.multiply(TAX_RATE);
        BigDecimal total = subtotal.add(tax);

        String orderNumber = "ORD-" + orderSequence.incrementAndGet();
        var order = new Order(orderNumber, request.customerId(), subtotal, total);
        order.setTaxAmount(tax);
        order.setShippingAddress(request.shippingAddress());
        order.setNotes(request.notes());

        for (var itemReq : request.items()) {
            var orderItem = new OrderItem(itemReq.productId(), itemReq.productName(),
                    itemReq.productSku(), itemReq.quantity(), itemReq.unitPrice());
            order.addItem(orderItem);
        }

        order = orderRepository.save(order);
        log.info("Order created: {}", order.getOrderNumber());

        eventPublisher.publish(DomainEvent.create("OrderCreated", "sales-service",
                new OrderEventPayload(order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getTotalAmount())));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return toResponse(findOrderOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, String status) {
        log.info("Updating order {} status to {}", orderId, status);
        Order order = findOrderOrThrow(orderId);

        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(status.toUpperCase());
        validateStatusTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        order = orderRepository.save(order);

        eventPublisher.publish(DomainEvent.create("OrderStatusUpdated", "sales-service",
                new OrderEventPayload(order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getTotalAmount())));

        return toResponse(order);
    }

    @Transactional
    public OrderResponse markPaymentCaptured(UUID orderId, UUID paymentId) {
        log.info("Marking order {} as paid for payment {}", orderId, paymentId);
        Order order = findOrderOrThrow(orderId);
        if (order.getPaymentStatus() == Order.PaymentStatus.CAPTURED) {
            return toResponse(order);
        }

        order.setPaymentStatus(Order.PaymentStatus.CAPTURED);
        if (order.getStatus() == Order.OrderStatus.PENDING) {
            order.setStatus(Order.OrderStatus.CONFIRMED);
        }
        order = orderRepository.save(order);

        eventPublisher.publish(DomainEvent.create("OrderPaymentCaptured", "sales-service",
                new OrderPaymentEventPayload(order.getId(), paymentId, order.getCustomerId(), order.getTotalAmount())));

        return toResponse(order);
    }

    @Transactional
    public OrderResponse markPaymentFailed(UUID orderId, UUID paymentId) {
        log.info("Marking order {} payment as failed for payment {}", orderId, paymentId);
        Order order = findOrderOrThrow(orderId);
        if (order.getPaymentStatus() == Order.PaymentStatus.FAILED) {
            return toResponse(order);
        }

        releasePaymentFailedReservations(orderId, order);

        order.setPaymentStatus(Order.PaymentStatus.FAILED);
        order = orderRepository.save(order);

        eventPublisher.publish(DomainEvent.create("OrderPaymentFailed", "sales-service",
                new OrderPaymentEventPayload(order.getId(), paymentId, order.getCustomerId(), order.getTotalAmount())));

        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String reason) {
        log.info("Cancelling order {}: {}", orderId, reason);
        Order order = findOrderOrThrow(orderId);

        SecurityUtils.verifyOwnershipOrAdmin(order.getCustomerId());

        if (order.getStatus() == Order.OrderStatus.CANCELLED || order.getStatus() == Order.OrderStatus.RETURNED
                || order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new BusinessRuleViolationException("INVALID_CANCEL",
                    "ステータス " + order.getStatus() + " の注文はキャンセルできません");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setNotes(order.getNotes() != null ? order.getNotes() + " | Cancel: " + reason : "Cancel: " + reason);
        order = orderRepository.save(order);

        eventPublisher.publish(DomainEvent.create("OrderCancelled", "sales-service",
                new OrderEventPayload(order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getTotalAmount())));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        return toResponse(orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber)));
    }

    // --- Shipments ---

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        log.info("Creating shipment for order: {}", request.orderId());
        findOrderOrThrow(request.orderId());

        var shipment = new Shipment(request.orderId(), request.carrier());
        shipment.setTrackingNumber(request.trackingNumber());
        shipment = shipmentRepository.save(shipment);

        eventPublisher.publish(DomainEvent.create("ShipmentCreated", "sales-service",
                new ShipmentEventPayload(shipment.getId(), request.orderId(), request.carrier())));

        return toShipmentResponse(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipment(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId.toString()));
        return toShipmentResponse(shipment);
    }

    @Transactional(readOnly = true)
    public java.util.List<ShipmentResponse> getShipmentsByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId).stream()
                .map(this::toShipmentResponse).toList();
    }

    @Transactional
    public ShipmentResponse updateShipmentStatus(UUID shipmentId, String status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId.toString()));

        Shipment.ShipmentStatus newStatus = Shipment.ShipmentStatus.valueOf(status.toUpperCase());
        shipment.setStatus(newStatus);
        if (newStatus == Shipment.ShipmentStatus.SHIPPED) shipment.setShippedAt(Instant.now());
        if (newStatus == Shipment.ShipmentStatus.DELIVERED) shipment.setDeliveredAt(Instant.now());

        return toShipmentResponse(shipmentRepository.save(shipment));
    }

    // --- Returns ---

    @Transactional
    public ReturnResponse createReturn(CreateReturnRequest request) {
        log.info("Creating return for order: {}", request.orderId());
        findOrderOrThrow(request.orderId());

        String returnNumber = "RET-" + orderSequence.incrementAndGet();
        var returnReq = new ReturnRequest(returnNumber, request.orderId(), request.customerId(), request.reason());
        returnReq = returnRequestRepository.save(returnReq);

        eventPublisher.publish(DomainEvent.create("ReturnRequested", "sales-service",
                new ReturnEventPayload(returnReq.getId(), returnNumber, request.orderId())));

        return toReturnResponse(returnReq);
    }

    @Transactional(readOnly = true)
    public Page<ReturnResponse> getReturns(Pageable pageable) {
        return returnRequestRepository.findAll(pageable).map(this::toReturnResponse);
    }

    @Transactional(readOnly = true)
    public ReturnResponse getReturn(UUID returnId) {
        ReturnRequest returnReq = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return", returnId.toString()));
        return toReturnResponse(returnReq);
    }

    @Transactional(readOnly = true)
    public Page<ReturnResponse> getReturnsByOrderId(UUID orderId, Pageable pageable) {
        return returnRequestRepository.findByOrderId(orderId, pageable).map(this::toReturnResponse);
    }

    @Transactional
    public ReturnResponse updateReturnStatus(UUID returnId, String status) {
        log.info("Updating return {} status to {}", returnId, status);
        ReturnRequest returnReq = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return", returnId.toString()));

        ReturnRequest.ReturnStatus newStatus = ReturnRequest.ReturnStatus.valueOf(status.toUpperCase());
        returnReq.setStatus(newStatus);
        returnReq = returnRequestRepository.save(returnReq);

        if (newStatus == ReturnRequest.ReturnStatus.REFUNDED) {
            eventPublisher.publish(DomainEvent.create("ReturnProcessed", "sales-service",
                    new ReturnEventPayload(returnReq.getId(), returnReq.getReturnNumber(), returnReq.getOrderId())));
        }

        return toReturnResponse(returnReq);
    }

    // --- Helpers ---

    private Order findOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));
    }

    private void validateStatusTransition(Order.OrderStatus current, Order.OrderStatus target) {
        if (current == Order.OrderStatus.CANCELLED || current == Order.OrderStatus.RETURNED) {
            throw new BusinessRuleViolationException("INVALID_STATUS_TRANSITION",
                    "ステータス " + current + " から " + target + " への変更はできません");
        }
    }

    private void releasePaymentFailedReservations(UUID orderId, Order order) {
        Map<String, Integer> quantitiesBySku = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            String sku = resolveReleaseSku(item);
            if (sku == null || item.getQuantity() <= 0) {
                continue;
            }
            quantitiesBySku.put(sku, quantitiesBySku.getOrDefault(sku, 0) + item.getQuantity());
        }

        String referenceId = orderId.toString();
        for (Map.Entry<String, Integer> entry : quantitiesBySku.entrySet()) {
            inventoryClient.releaseReservation(entry.getKey(), entry.getValue(), referenceId, PAYMENT_FAILED_RELEASE_REASON);
        }
    }

    private String resolveReleaseSku(OrderItem item) {
        String productSku = item.getProductSku();
        if (productSku != null && !productSku.isBlank()) {
            return productSku.trim();
        }
        String productId = item.getProductId();
        if (productId != null && !productId.isBlank()) {
            return productId.trim();
        }
        return null;
    }

    private OrderResponse toResponse(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getProductId(), i.getProductName(),
                        i.getProductSku(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getCustomerId(),
                order.getStatus().name(), order.getPaymentStatus().name(),
                order.getSubtotalAmount(), order.getTaxAmount(), order.getShippingAmount(),
                order.getDiscountAmount(), order.getTotalAmount(), order.getCurrency(),
                order.getPaymentMethod(), order.getShippingAddress(), items,
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private ShipmentResponse toShipmentResponse(Shipment s) {
        return new ShipmentResponse(s.getId(), s.getOrderId(), s.getCarrier(), s.getTrackingNumber(),
                s.getStatus().name(), s.getShippedAt(), s.getDeliveredAt(), s.getCreatedAt());
    }

    private ReturnResponse toReturnResponse(ReturnRequest r) {
        return new ReturnResponse(r.getId(), r.getReturnNumber(), r.getOrderId(), r.getCustomerId(),
                r.getReason(), r.getStatus().name(), r.getQuantity(), r.getRefundAmount(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    public record OrderEventPayload(UUID orderId, String orderNumber, UUID customerId, BigDecimal totalAmount) {}
    public record OrderPaymentEventPayload(UUID orderId, UUID paymentId, UUID customerId, BigDecimal totalAmount) {}
    public record ShipmentEventPayload(UUID shipmentId, UUID orderId, String carrier) {}
    public record ReturnEventPayload(UUID returnId, String returnNumber, UUID orderId) {}
}
