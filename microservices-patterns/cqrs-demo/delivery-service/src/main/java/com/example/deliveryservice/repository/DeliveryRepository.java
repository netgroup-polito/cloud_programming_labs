package com.example.deliveryservice.repository;

import com.example.deliveryservice.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
}
