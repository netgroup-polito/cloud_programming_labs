package com.example.orderservice.service;

import com.example.orderservice.config.RabbitConfig;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.event.CreditReservationResult;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    public OrderService(OrderRepository orderRepository, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * STEP 1: Create order in PENDING state
     * STEP 2: Publish OrderCreated event
     */
    public Order createOrder(CreateOrderRequest request) {
        log.info(">>> STEP 1: Creating order for customer {} with total {}",
                request.getCustomerId(), request.getOrderTotal());

        Order order = new Order(request.getCustomerId(), request.getOrderTotal());
        order = orderRepository.save(order);

        log.info(">>> STEP 1: Order {} created in PENDING state", order.getId());

        // Publish event
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getOrderTotal());

        log.info(">>> STEP 2: Publishing OrderCreated event: {}", event);
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE, RabbitConfig.ORDER_CREATED_KEY, event);

        return order;
    }

    /**
     * STEP 5: Handle the credit reservation result from CustomerService
     */
    @RabbitListener(queues = RabbitConfig.CREDIT_RESULT_QUEUE)
    public void handleCreditResult(CreditReservationResult result) {
        log.info(">>> STEP 5: Received credit reservation result: {}", result);

        Order order = orderRepository.findById(result.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + result.getOrderId()));

        if (result.isApproved()) {
            order.setStatus(OrderStatus.APPROVED);
            log.info(">>> STEP 5: Order {} APPROVED", order.getId());
        } else {
            order.setStatus(OrderStatus.REJECTED);
            log.info(">>> STEP 5: Order {} REJECTED - Reason: {}", order.getId(), result.getReason());
        }

        orderRepository.save(order);
    }
}
