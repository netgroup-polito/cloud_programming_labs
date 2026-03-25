package com.example.orderhistoryservice.model;

public class OrderEvent {

    private String timestamp;
    private String eventType;
    private String serviceName;
    private String details;

    public OrderEvent() {
    }

    public OrderEvent(String timestamp, String eventType, String serviceName, String details) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.serviceName = serviceName;
        this.details = details;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
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

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
