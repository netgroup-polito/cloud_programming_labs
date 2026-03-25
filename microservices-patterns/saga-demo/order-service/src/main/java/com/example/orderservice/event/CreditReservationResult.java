package com.example.orderservice.event;

public class CreditReservationResult {

    private Long orderId;
    private boolean approved;
    private String reason;

    public CreditReservationResult() {}

    public CreditReservationResult(Long orderId, boolean approved, String reason) {
        this.orderId = orderId;
        this.approved = approved;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return "CreditReservationResult{orderId=" + orderId + ", approved=" + approved + ", reason='" + reason + "'}";
    }
}
