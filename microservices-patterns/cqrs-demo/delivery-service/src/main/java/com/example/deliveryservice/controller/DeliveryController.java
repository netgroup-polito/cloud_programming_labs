package com.example.deliveryservice.controller;

import com.example.deliveryservice.config.RabbitConfig;
import com.example.deliveryservice.event.DomainEvent;
import com.example.deliveryservice.model.Delivery;
import com.example.deliveryservice.repository.DeliveryRepository;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/deliveries")
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryRepository deliveryRepository;
    private final RabbitTemplate rabbitTemplate;

    public DeliveryController(DeliveryRepository deliveryRepository, RabbitTemplate rabbitTemplate) {
        this.deliveryRepository = deliveryRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Delivery createDelivery(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String address = (String) request.get("address");

        Delivery delivery = new Delivery(orderId, address);
        delivery = deliveryRepository.save(delivery);

        Map<String, Object> data = new HashMap<>();
        data.put("deliveryId", delivery.getId());
        data.put("orderId", delivery.getOrderId());
        data.put("address", delivery.getAddress());

        DomainEvent event = new DomainEvent(
                orderId,
                "DELIVERY_SCHEDULED",
                "delivery-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing DELIVERY_SCHEDULED event for order {}", orderId);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "delivery.scheduled", event);

        return delivery;
    }

    @PutMapping("/{id}/pickup")
    public Delivery pickupDelivery(@PathVariable Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        delivery.setStatus("PICKED_UP");
        delivery = deliveryRepository.save(delivery);

        Map<String, Object> data = new HashMap<>();
        data.put("deliveryId", delivery.getId());
        data.put("orderId", delivery.getOrderId());

        DomainEvent event = new DomainEvent(
                delivery.getOrderId(),
                "DELIVERY_PICKED_UP",
                "delivery-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing DELIVERY_PICKED_UP event for order {}", delivery.getOrderId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "delivery.pickedUp", event);

        return delivery;
    }

    @PutMapping("/{id}/deliver")
    public Delivery deliverDelivery(@PathVariable Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        delivery.setStatus("DELIVERED");
        delivery.setDeliveredAt(LocalDateTime.now());
        delivery = deliveryRepository.save(delivery);

        Map<String, Object> data = new HashMap<>();
        data.put("deliveryId", delivery.getId());
        data.put("orderId", delivery.getOrderId());

        DomainEvent event = new DomainEvent(
                delivery.getOrderId(),
                "DELIVERY_DELIVERED",
                "delivery-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing DELIVERY_DELIVERED event for order {}", delivery.getOrderId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "delivery.delivered", event);

        return delivery;
    }

    @GetMapping
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    @GetMapping("/{id}")
    public Delivery getDelivery(@PathVariable Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
