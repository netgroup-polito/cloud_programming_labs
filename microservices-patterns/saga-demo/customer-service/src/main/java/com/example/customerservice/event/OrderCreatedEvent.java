package com.example.customerservice.event;

import java.math.BigDecimal;

public class OrderCreatedEvent {

    private Long orderId;
    private Long customerId;
    private BigDecimal orderTotal;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(Long orderId, Long customerId, BigDecimal orderTotal) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.orderTotal = orderTotal;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public BigDecimal getOrderTotal() { return orderTotal; }
    public void setOrderTotal(BigDecimal orderTotal) { this.orderTotal = orderTotal; }

    @Override
    public String toString() {
        return "OrderCreatedEvent{orderId=" + orderId + ", customerId=" + customerId + ", orderTotal=" + orderTotal + "}";
    }
}
