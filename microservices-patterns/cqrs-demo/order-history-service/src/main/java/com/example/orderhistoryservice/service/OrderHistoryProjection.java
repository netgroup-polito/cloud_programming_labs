package com.example.orderhistoryservice.service;

import com.example.orderhistoryservice.config.RabbitConfig;
import com.example.orderhistoryservice.event.DomainEvent;
import com.example.orderhistoryservice.model.OrderEvent;
import com.example.orderhistoryservice.model.OrderHistoryView;
import com.example.orderhistoryservice.repository.OrderHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class OrderHistoryProjection {

    private static final Logger log = LoggerFactory.getLogger(OrderHistoryProjection.class);

    private final OrderHistoryRepository repository;

    public OrderHistoryProjection(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handleEvent(DomainEvent event) {
        log.info(">>> Received event: {} from {} for order {}",
                event.getEventType(), event.getServiceName(), event.getOrderId());

        OrderHistoryView view = repository.findByOrderId(event.getOrderId())
                .orElseGet(() -> {
                    OrderHistoryView newView = new OrderHistoryView();
                    newView.setOrderId(event.getOrderId());
                    newView.setId(event.getOrderId().toString());
                    return newView;
                });

        Map<String, Object> data = event.getData();

        switch (event.getEventType()) {
            case "ORDER_CREATED":
                view.setCustomerName(data.get("customerName").toString());
                view.setItems(data.get("items").toString());
                view.setTotalAmount(Double.valueOf(data.get("totalAmount").toString()));
                view.setOrderStatus("CREATED");
                break;
            case "ORDER_CONFIRMED":
                view.setOrderStatus("CONFIRMED");
                break;
            case "TICKET_CREATED":
                view.setTicketStatus("CREATED");
                break;
            case "TICKET_ACCEPTED":
                view.setTicketStatus("ACCEPTED");
                break;
            case "DELIVERY_SCHEDULED":
                view.setDeliveryStatus("SCHEDULED");
                if (data != null && data.get("address") != null) {
                    view.setDeliveryAddress(data.get("address").toString());
                }
                break;
            case "DELIVERY_PICKED_UP":
                view.setDeliveryStatus("PICKED_UP");
                break;
            case "DELIVERY_DELIVERED":
                view.setDeliveryStatus("DELIVERED");
                break;
            case "INVOICE_CREATED":
                view.setInvoiceStatus("CREATED");
                view.setInvoiceAmount(Double.valueOf(data.get("amount").toString()));
                break;
            default:
                log.warn(">>> Unknown event type: {}", event.getEventType());
                break;
        }

        view.setLastUpdated(Instant.now().toString());

        OrderEvent orderEvent = new OrderEvent(
                event.getTimestamp(),
                event.getEventType(),
                event.getServiceName(),
                buildDetails(event)
        );
        view.addEvent(orderEvent);

        repository.save(view);

        log.info(">>> Updated Order History View for order {}: orderStatus={}, ticketStatus={}, deliveryStatus={}, invoiceStatus={}",
                event.getOrderId(),
                view.getOrderStatus(),
                view.getTicketStatus(),
                view.getDeliveryStatus(),
                view.getInvoiceStatus());
    }

    private String buildDetails(DomainEvent event) {
        Map<String, Object> data = event.getData();

        switch (event.getEventType()) {
            case "ORDER_CREATED":
                if (data != null && data.get("customerName") != null && data.get("items") != null) {
                    return "Order created by " + data.get("customerName").toString()
                            + " for " + data.get("items").toString();
                }
                return "Order created";
            case "ORDER_CONFIRMED":
                return "Order confirmed";
            case "TICKET_CREATED":
                return "Kitchen ticket created";
            case "TICKET_ACCEPTED":
                return "Kitchen ticket accepted";
            case "DELIVERY_SCHEDULED":
                if (data != null && data.get("address") != null) {
                    return "Delivery scheduled to " + data.get("address").toString();
                }
                return "Delivery scheduled";
            case "DELIVERY_PICKED_UP":
                return "Delivery picked up by driver";
            case "DELIVERY_DELIVERED":
                return "Delivery completed";
            case "INVOICE_CREATED":
                if (data != null && data.get("amount") != null) {
                    return "Invoice created for $" + data.get("amount").toString();
                }
                return "Invoice created";
            default:
                return "Event: " + event.getEventType();
        }
    }
}
