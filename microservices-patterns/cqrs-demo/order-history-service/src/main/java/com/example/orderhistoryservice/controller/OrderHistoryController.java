package com.example.orderhistoryservice.controller;

import com.example.orderhistoryservice.model.OrderHistoryView;
import com.example.orderhistoryservice.repository.OrderHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/order-history")
public class OrderHistoryController {

    private final OrderHistoryRepository repository;

    public OrderHistoryController(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<OrderHistoryView> findAll() {
        return repository.findAll();
    }

    @GetMapping("/{orderId}")
    public OrderHistoryView findByOrderId(@PathVariable Long orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order history not found"));
    }
}
