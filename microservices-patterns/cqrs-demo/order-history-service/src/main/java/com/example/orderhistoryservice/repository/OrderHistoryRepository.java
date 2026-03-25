package com.example.orderhistoryservice.repository;

import com.example.orderhistoryservice.model.OrderHistoryView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrderHistoryRepository extends MongoRepository<OrderHistoryView, String> {

    Optional<OrderHistoryView> findByOrderId(Long orderId);
}
