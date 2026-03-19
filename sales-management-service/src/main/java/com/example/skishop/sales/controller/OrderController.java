package com.example.skishop.sales.controller;

import com.example.skishop.common.security.SecurityUtils;
import com.example.skishop.sales.dto.*;
import com.example.skishop.sales.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // --- Order endpoints ---

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Create order request for customer: {}", request.customerId());
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        OrderResponse response = orderService.getOrder(orderId);
        SecurityUtils.verifyOwnershipOrAdmin(response.customerId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber) {
        OrderResponse response = orderService.getOrderByNumber(orderNumber);
        SecurityUtils.verifyOwnershipOrAdmin(response.customerId());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("#customerId == authentication.principal or hasRole('ADMIN')")
    @GetMapping("/orders/customer/{customerId}")
    public ResponseEntity<Page<OrderResponse>> getCustomerOrders(@PathVariable UUID customerId,
                                                                  @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getCustomerOrders(customerId, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/orders")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(@PageableDefault(size = 20, sort = "createdAt",
            direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable UUID orderId, @RequestParam String status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, status));
    }

    @PutMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId, @RequestParam String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, reason));
    }

    // --- Shipment endpoints ---

    @PostMapping("/shipments")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentResponse response = orderService.createShipment(request);
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + response.id())).body(response);
    }

    @GetMapping("/shipments/{id}")
    public ResponseEntity<ShipmentResponse> getShipment(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getShipment(id));
    }

    @GetMapping("/shipments/order/{orderId}")
    public ResponseEntity<List<ShipmentResponse>> getShipmentsByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getShipmentsByOrderId(orderId));
    }

    @PutMapping("/shipments/{id}/status")
    public ResponseEntity<ShipmentResponse> updateShipmentStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(orderService.updateShipmentStatus(id, status));
    }

    // --- Return endpoints ---

    @PostMapping("/returns")
    public ResponseEntity<ReturnResponse> createReturn(@Valid @RequestBody CreateReturnRequest request) {
        ReturnResponse response = orderService.createReturn(request);
        return ResponseEntity.created(URI.create("/api/v1/returns/" + response.id())).body(response);
    }

    @GetMapping("/returns/{id}")
    public ResponseEntity<ReturnResponse> getReturn(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getReturn(id));
    }

    @GetMapping("/returns")
    public ResponseEntity<Page<ReturnResponse>> getReturns(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getReturns(pageable));
    }

    @GetMapping("/returns/order/{orderId}")
    public ResponseEntity<Page<ReturnResponse>> getReturnsByOrderId(@PathVariable UUID orderId,
                                                                     @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getReturnsByOrderId(orderId, pageable));
    }

    @PutMapping("/returns/{id}/status")
    public ResponseEntity<ReturnResponse> updateReturnStatus(@PathVariable UUID id,
                                                              @Valid @RequestBody ReturnStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateReturnStatus(id, request.status()));
    }
}
