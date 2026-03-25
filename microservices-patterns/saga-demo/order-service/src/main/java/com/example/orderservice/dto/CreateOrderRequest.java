package com.example.orderservice.dto;

import java.math.BigDecimal;

public class CreateOrderRequest {

    private Long customerId;
    private BigDecimal orderTotal;

    public CreateOrderRequest() {}

    public CreateOrderRequest(Long customerId, BigDecimal orderTotal) {
        this.customerId = customerId;
        this.orderTotal = orderTotal;
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public BigDecimal getOrderTotal() { return orderTotal; }
    public void setOrderTotal(BigDecimal orderTotal) { this.orderTotal = orderTotal; }
}
