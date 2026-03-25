package com.example.ticketservice.event;

import java.util.Map;

public class DomainEvent {

    private Long orderId;
    private String eventType;
    private String serviceName;
    private String timestamp;
    private Map<String, Object> data;

    public DomainEvent() {
    }

    public DomainEvent(Long orderId, String eventType, String serviceName, String timestamp, Map<String, Object> data) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.serviceName = serviceName;
        this.timestamp = timestamp;
        this.data = data;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "DomainEvent{" +
                "orderId=" + orderId +
                ", eventType='" + eventType + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", data=" + data +
                '}';
    }
}
