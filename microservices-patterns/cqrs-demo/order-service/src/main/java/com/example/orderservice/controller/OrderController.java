package com.example.orderservice.controller;

import com.example.orderservice.config.RabbitConfig;
import com.example.orderservice.event.DomainEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    public OrderController(OrderRepository orderRepository, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(@RequestBody Order order) {
        Order newOrder = new Order(
                order.getCustomerId(),
                order.getCustomerName(),
                order.getItems(),
                order.getTotalAmount()
        );
        Order savedOrder = orderRepository.save(newOrder);

        Map<String, Object> data = new HashMap<>();
        data.put("customerId", savedOrder.getCustomerId());
        data.put("customerName", savedOrder.getCustomerName());
        data.put("items", savedOrder.getItems());
        data.put("totalAmount", savedOrder.getTotalAmount());

        DomainEvent event = new DomainEvent(
                savedOrder.getId(),
                "ORDER_CREATED",
                "order-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing ORDER_CREATED event for order {}", savedOrder.getId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "order.created", event);

        return savedOrder;
    }

    @PutMapping("/{id}/confirm")
    public Order confirmOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        order.setStatus("CONFIRMED");
        Order savedOrder = orderRepository.save(order);

        Map<String, Object> data = new HashMap<>();
        data.put("customerId", savedOrder.getCustomerId());
        data.put("customerName", savedOrder.getCustomerName());
        data.put("items", savedOrder.getItems());
        data.put("totalAmount", savedOrder.getTotalAmount());

        DomainEvent event = new DomainEvent(
                savedOrder.getId(),
                "ORDER_CONFIRMED",
                "order-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing ORDER_CONFIRMED event for order {}", savedOrder.getId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "order.confirmed", event);

        return savedOrder;
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
