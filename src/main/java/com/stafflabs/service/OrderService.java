package com.stafflabs.service;

import com.stafflabs.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private static final String[] STATUSES = { "PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED" };
    private static final String[] PRODUCTS = {
            "Laptop Pro X1", "Wireless Mouse", "Mechanical Keyboard",
            "USB-C Hub", "Monitor 27in", "Desk Lamp LED",
            "Ergonomic Chair", "Notebook Set", "Pen Collection", "Cable Organizer"
    };

    /**
     * High-speed batch insertion using JdbcTemplate
     */
    @Transactional
    public long seedOrders(int count) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Starting batch seed of {} orders", count);

        String sql = """
                INSERT INTO orders (order_number, customer_email, customer_name, product_sku,
                                   product_name, quantity, unit_price, total_amount, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int batchSize = 1000;
        int totalBatches = (count + batchSize - 1) / batchSize;

        for (int batch = 0; batch < totalBatches; batch++) {
            final int finalBatch = batch;
            final int finalBatchSize = batchSize;
            int currentBatchSize = Math.min(batchSize, count - (batch * batchSize));

            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    int globalIndex = finalBatch * finalBatchSize + i;
                    Random rand = ThreadLocalRandom.current();

                    String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-"
                            + String.format("%06d", globalIndex);
                    String customerEmail = "customer" + (rand.nextInt(10000)) + "@example.com";
                    String customerName = "Customer " + (rand.nextInt(10000));
                    String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];
                    String productSku = "SKU-" + String.format("%06d", rand.nextInt(100000));
                    int quantity = rand.nextInt(1, 10);
                    BigDecimal unitPrice = BigDecimal.valueOf(rand.nextDouble(10, 1000)).setScale(2,
                            RoundingMode.HALF_UP);
                    BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
                    String status = STATUSES[rand.nextInt(STATUSES.length)];
                    LocalDateTime createdAt = LocalDateTime.now().minusDays(rand.nextInt(365));

                    ps.setString(1, orderNumber);
                    ps.setString(2, customerEmail);
                    ps.setString(3, customerName);
                    ps.setString(4, productSku);
                    ps.setString(5, product);
                    ps.setInt(6, quantity);
                    ps.setBigDecimal(7, unitPrice);
                    ps.setBigDecimal(8, totalAmount);
                    ps.setString(9, status);
                    ps.setObject(10, createdAt);
                }

                @Override
                public int getBatchSize() {
                    return currentBatchSize;
                }
            });

            if (batch % 10 == 0) {
                log.info("Seeded batch {}/{}", batch + 1, totalBatches);
            }
        }

        sample.stop(meterRegistry.timer("db.seed.duration"));
        log.info("Completed seeding {} orders", count);

        return orderRepository.count();
    }

    /**
     * Execute the slow query with GROUP BY on unindexed columns
     */
    @Transactional(readOnly = true)
    public List<Object[]> getOrderStatistics() {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Executing slow query with GROUP BY on unindexed columns");

        List<Object[]> results = orderRepository.findOrderStatsByCustomerAndStatus();

        sample.stop(meterRegistry.timer("db.slow.query.duration"));
        log.info("Query returned {} results", results.size());

        return results;
    }
}
