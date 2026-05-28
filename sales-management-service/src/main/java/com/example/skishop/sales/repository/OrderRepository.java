package com.example.skishop.sales.repository;

import com.example.skishop.sales.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = "items")
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Order> findByStatus(Order.OrderStatus status, Pageable pageable);

    // ============================================================
    // Analytics aggregation queries
    // ------------------------------------------------------------
    // 集計対象: CANCELLED / RETURNED 以外を「有効注文」とみなす。
    // PENDING も売上見込みとして含める（管理画面ダッシュボード仕様）。
    // ============================================================

    /** 期間内の日別売上 [date, revenue, orders]。 */
    @Query(value = """
            SELECT CAST(o.created_at AS date) AS d,
                   COALESCE(SUM(o.total_amount), 0) AS revenue,
                   COUNT(*) AS orders_count
            FROM orders o
            WHERE o.created_at >= :since
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY CAST(o.created_at AS date)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> sumDailyRevenue(@Param("since") Instant since);

    /** 期間内のユニーク購入者数を日別 [date, unique_customers]。 */
    @Query(value = """
            SELECT CAST(o.created_at AS date) AS d,
                   COUNT(DISTINCT o.customer_id) AS unique_customers
            FROM orders o
            WHERE o.created_at >= :since
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY CAST(o.created_at AS date)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countDailyUniqueCustomers(@Param("since") Instant since);

    /** 期間内の合計売上 / 注文数 [total_revenue, order_count]。 */
    @Query(value = """
            SELECT COALESCE(SUM(o.total_amount), 0) AS total_revenue,
                   COUNT(*) AS order_count
            FROM orders o
            WHERE o.created_at >= :since
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            """, nativeQuery = true)
    List<Object[]> totalRevenueSince(@Param("since") Instant since);

    /** 期間内の Top N 商品 (販売数量順) [productId, productName, quantity, revenue]。 */
    @Query(value = """
            SELECT oi.product_id,
                   MAX(oi.product_name) AS product_name,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.created_at >= :since
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY oi.product_id
            ORDER BY quantity DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topProductsSince(@Param("since") Instant since, @Param("limit") int limit);

    /** 期間内の商品別集計 (カテゴリ別集計用) [productId, quantity, revenue]。 */
    @Query(value = """
            SELECT oi.product_id,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.created_at >= :since
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY oi.product_id
            """, nativeQuery = true)
    List<Object[]> productRevenueSince(@Param("since") Instant since);

    @Query(value = """
            SELECT COUNT(*) AS order_count,
                   COALESCE(SUM(o.total_amount), 0) AS total_amount
            FROM orders o
            WHERE o.customer_id = :customerId
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            """, nativeQuery = true)
    List<Object[]> customerPurchaseSummary(@Param("customerId") UUID customerId);

    @Query(value = """
            SELECT COALESCE(NULLIF(oi.product_sku, ''), NULLIF(oi.product_name, ''), oi.product_id) AS label,
                   SUM(oi.quantity) AS quantity
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.customer_id = :customerId
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY label
            ORDER BY quantity DESC, label ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topPurchasedLabelsByCustomer(@Param("customerId") UUID customerId, @Param("limit") int limit);

    @Query(value = """
            SELECT oi.product_id AS product_id,
                   SUM(oi.quantity) AS quantity
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.customer_id = :customerId
              AND o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY oi.product_id
            ORDER BY quantity DESC, product_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topPurchasedProductIdsByCustomer(@Param("customerId") UUID customerId, @Param("limit") int limit);
}
