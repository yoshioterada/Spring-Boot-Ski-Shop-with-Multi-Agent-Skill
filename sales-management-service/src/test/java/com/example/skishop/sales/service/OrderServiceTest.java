package com.example.skishop.sales.service;

import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.AuthorizationDeniedException;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.sales.dto.*;
import com.example.skishop.sales.dto.CreateOrderRequest.OrderItemRequest;
import com.example.skishop.sales.model.Order;
import com.example.skishop.sales.model.ReturnRequest;
import com.example.skishop.sales.model.Shipment;
import com.example.skishop.sales.repository.OrderRepository;
import com.example.skishop.sales.repository.ReturnRequestRepository;
import com.example.skishop.sales.repository.ShipmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private EventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, shipmentRepository, returnRequestRepository, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private Order createTestOrder() {
        return new Order("ORD-1", UUID.randomUUID(), BigDecimal.valueOf(10000), BigDecimal.valueOf(11000));
    }

    @Nested
    @DisplayName("注文作成")
    class CreateOrder {

        @Test
        @DisplayName("有効な情報で注文作成が成功する")
        void should_createOrder_when_validRequest() {
            UUID customerId = UUID.randomUUID();
            var items = List.of(new OrderItemRequest("prod-1", "Ski Boots", "SKI-001", 2, BigDecimal.valueOf(29800)));
            var request = new CreateOrderRequest(customerId, items, "東京都新宿区1-1-1", null);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(request);

            assertThat(response).isNotNull();
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.items()).hasSize(1);
            assertThat(response.subtotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(59600));
            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("注文取得")
    class GetOrder {

        @Test
        @DisplayName("IDで注文取得が成功する")
        void should_returnOrder_when_exists() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrder(orderId);

            assertThat(response).isNotNull();
            assertThat(response.orderNumber()).isEqualTo("ORD-1");
        }

        @Test
        @DisplayName("存在しない注文IDでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_orderNotExists() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(orderId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("注文番号で注文取得が成功する")
        void should_returnOrder_when_orderNumberExists() {
            var order = createTestOrder();
            when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrderByNumber("ORD-1");

            assertThat(response.orderNumber()).isEqualTo("ORD-1");
        }

        @Test
        @DisplayName("存在しない注文番号でResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_orderNumberNotExists() {
            when(orderRepository.findByOrderNumber("ORD-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderByNumber("ORD-999"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("顧客IDで注文一覧取得が成功する")
        void should_returnOrders_when_validCustomerId() {
            UUID customerId = UUID.randomUUID();
            var order = new Order("ORD-1", customerId, BigDecimal.valueOf(10000), BigDecimal.valueOf(11000));
            Pageable pageable = PageRequest.of(0, 20);
            when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable))
                    .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

            Page<OrderResponse> result = orderService.getCustomerOrders(customerId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().customerId()).isEqualTo(customerId);
        }
    }

    @Nested
    @DisplayName("全注文一覧取得（管理者用）")
    class GetAllOrders {

        @Test
        @DisplayName("全注文のページネーション取得が成功する")
        void should_returnAllOrders_when_requested() {
            UUID customerId = UUID.randomUUID();
            var order = new Order("ORD-1", customerId, BigDecimal.valueOf(10000), BigDecimal.valueOf(11000));
            Pageable pageable = PageRequest.of(0, 20);
            when(orderRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

            Page<OrderResponse> result = orderService.getAllOrders(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().orderNumber()).isEqualTo("ORD-1");
        }
    }

    @Nested
    @DisplayName("注文ステータス更新")
    class UpdateOrderStatus {

        @Test
        @DisplayName("注文ステータス更新が成功する")
        void should_updateStatus_when_validTransition() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.updateOrderStatus(orderId, "CONFIRMED");

            assertThat(response.status()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("キャンセル済み注文のステータス変更で例外がスローされる")
        void should_throwException_when_cancelledOrderStatusChange() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            order.setStatus(Order.OrderStatus.CANCELLED);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, "CONFIRMED"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("変更はできません");
        }
    }

    @Nested
    @DisplayName("注文キャンセル")
    class CancelOrder {

        @Test
        @DisplayName("注文キャンセルが成功する")
        void should_cancelOrder_when_validOrder() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            setupSecurityContext(order.getCustomerId());
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancelOrder(orderId, "顧客都合");

            assertThat(response.status()).isEqualTo("CANCELLED");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("配送済み注文のキャンセルで例外がスローされる")
        void should_throwException_when_deliveredOrderCancel() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            setupSecurityContext(order.getCustomerId());
            order.setStatus(Order.OrderStatus.DELIVERED);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(orderId, "顧客都合"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("他人の注文はキャンセルできない")
        void should_throwAuthorizationDenied_when_nonOwnerCancels() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            setupSecurityContext(UUID.randomUUID()); // 注文所有者ではない別ユーザー
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(orderId, "顧客都合"))
                    .isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    @Nested
    @DisplayName("配送管理")
    class ShipmentManagement {

        @Test
        @DisplayName("配送作成が成功する")
        void should_createShipment_when_validRequest() {
            UUID orderId = UUID.randomUUID();
            var order = createTestOrder();
            var request = new CreateShipmentRequest(orderId, "ヤマト運輸", "TRACK-001");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipmentResponse response = orderService.createShipment(request);

            assertThat(response.carrier()).isEqualTo("ヤマト運輸");
            assertThat(response.trackingNumber()).isEqualTo("TRACK-001");
            assertThat(response.status()).isEqualTo("PREPARING");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("配送ステータス更新が成功する")
        void should_updateShipmentStatus_when_validRequest() {
            UUID shipmentId = UUID.randomUUID();
            var shipment = new Shipment(UUID.randomUUID(), "ヤマト運輸");
            when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.of(shipment));
            when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

            ShipmentResponse result = orderService.updateShipmentStatus(shipmentId, "SHIPPED");

            assertThat(result.status()).isEqualTo("SHIPPED");
            assertThat(result.shippedAt()).isNotNull();
        }

        @Test
        @DisplayName("配送ID取得が成功する")
        void should_returnShipment_when_exists() {
            UUID shipmentId = UUID.randomUUID();
            var shipment = new Shipment(UUID.randomUUID(), "佐川急便");
            when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.of(shipment));

            ShipmentResponse result = orderService.getShipment(shipmentId);

            assertThat(result.carrier()).isEqualTo("佐川急便");
        }

        @Test
        @DisplayName("存在しない配送IDでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_shipmentNotExists() {
            UUID shipmentId = UUID.randomUUID();
            when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getShipment(shipmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("注文IDで配送一覧取得が成功する")
        void should_returnShipments_when_orderIdValid() {
            UUID orderId = UUID.randomUUID();
            var shipment = new Shipment(orderId, "ヤマト運輸");
            when(shipmentRepository.findByOrderId(orderId)).thenReturn(List.of(shipment));

            var result = orderService.getShipmentsByOrderId(orderId);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().carrier()).isEqualTo("ヤマト運輸");
        }
    }

    @Nested
    @DisplayName("返品管理")
    class ReturnManagement {

        @Test
        @DisplayName("返品作成が成功する")
        void should_createReturn_when_validRequest() {
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            var order = createTestOrder();
            var request = new CreateReturnRequest(orderId, customerId, "サイズが合わない");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            ReturnResponse response = orderService.createReturn(request);

            assertThat(response.reason()).isEqualTo("サイズが合わない");
            assertThat(response.status()).isEqualTo("REQUESTED");
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("返品ID取得が成功する")
        void should_returnReturn_when_exists() {
            UUID returnId = UUID.randomUUID();
            var ret = new ReturnRequest("RET-1", UUID.randomUUID(), UUID.randomUUID(), "不良品");
            when(returnRequestRepository.findById(returnId)).thenReturn(Optional.of(ret));

            ReturnResponse result = orderService.getReturn(returnId);

            assertThat(result.reason()).isEqualTo("不良品");
        }

        @Test
        @DisplayName("存在しない返品IDでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_returnNotExists() {
            UUID returnId = UUID.randomUUID();
            when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getReturn(returnId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("返品ステータス更新が成功する")
        void should_updateReturnStatus_when_validRequest() {
            UUID returnId = UUID.randomUUID();
            var ret = new ReturnRequest("RET-1", UUID.randomUUID(), UUID.randomUUID(), "不良品");
            when(returnRequestRepository.findById(returnId)).thenReturn(Optional.of(ret));
            when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            ReturnResponse result = orderService.updateReturnStatus(returnId, "APPROVED");

            assertThat(result.status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("注文IDで返品一覧取得が成功する")
        void should_returnReturns_when_orderIdValid() {
            UUID orderId = UUID.randomUUID();
            var ret = new ReturnRequest("RET-1", orderId, UUID.randomUUID(), "不良品");
            Pageable pageable = PageRequest.of(0, 20);
            when(returnRequestRepository.findByOrderId(orderId, pageable))
                    .thenReturn(new PageImpl<>(List.of(ret), pageable, 1));

            Page<ReturnResponse> result = orderService.getReturnsByOrderId(orderId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("返品一覧取得が成功する")
        void should_returnAllReturns() {
            var ret = new ReturnRequest("RET-1", UUID.randomUUID(), UUID.randomUUID(), "不良品");
            Pageable pageable = PageRequest.of(0, 20);
            when(returnRequestRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(ret), pageable, 1));

            Page<ReturnResponse> result = orderService.getReturns(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
