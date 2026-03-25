package com.example.customerservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private BigDecimal creditLimit;

    private BigDecimal creditReserved;

    public Customer() {}

    public Customer(String name, BigDecimal creditLimit) {
        this.name = name;
        this.creditLimit = creditLimit;
        this.creditReserved = BigDecimal.ZERO;
    }

    public BigDecimal availableCredit() {
        return creditLimit.subtract(creditReserved);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public BigDecimal getCreditReserved() { return creditReserved; }
    public void setCreditReserved(BigDecimal creditReserved) { this.creditReserved = creditReserved; }
}
