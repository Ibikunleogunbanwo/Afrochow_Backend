package com.afrochow.orderline.repository;
import com.afrochow.orderline.model.OrderLine;
import com.afrochow.order.model.Order;
import com.afrochow.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrder(Order order);

    List<OrderLine> findByProduct(Product product);


    @Query("SELECT ol.product, COUNT(ol) as orderCount FROM OrderLine ol GROUP BY ol.product ORDER BY orderCount DESC")
    List<Object[]> findMostOrderedProducts();


    Long countByProduct(Product product);
}