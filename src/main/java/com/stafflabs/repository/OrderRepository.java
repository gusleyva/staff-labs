package com.stafflabs.repository;

import com.stafflabs.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Deliberately inefficient query with GROUP BY and no indexes
     * This will cause high I/O and latency on large datasets
     */
    @Query("""
            SELECT o.status, o.customerEmail, COUNT(o.id) as orderCount, SUM(o.totalAmount) as totalRevenue
            FROM Order o
            WHERE o.status IN ('PENDING', 'PROCESSING', 'SHIPPED')
            GROUP BY o.status, o.customerEmail
            HAVING COUNT(o.id) > 1
            ORDER BY totalRevenue DESC
            """)
    List<Object[]> findOrderStatsByCustomerAndStatus();
}
