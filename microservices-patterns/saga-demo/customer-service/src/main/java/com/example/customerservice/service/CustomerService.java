package com.example.customerservice.service;

import com.example.customerservice.config.RabbitConfig;
import com.example.customerservice.event.CreditReservationResult;
import com.example.customerservice.event.OrderCreatedEvent;
import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final RabbitTemplate rabbitTemplate;

    public CustomerService(CustomerRepository customerRepository, RabbitTemplate rabbitTemplate) {
        this.customerRepository = customerRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * STEP 3: Receive OrderCreated event and attempt to reserve credit
     * STEP 4: Publish credit reservation result
     */
    @RabbitListener(queues = RabbitConfig.ORDER_CREATED_QUEUE)
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info(">>> STEP 3: Received OrderCreated event: {}", event);

        Customer customer = customerRepository.findById(event.getCustomerId()).orElse(null);

        CreditReservationResult result;

        if (customer == null) {
            log.warn(">>> STEP 3: Customer {} not found", event.getCustomerId());
            result = new CreditReservationResult(event.getOrderId(), false, "Customer not found");

        } else {
            log.info(">>> STEP 3: Customer {} ({}) - credit limit: {}, reserved: {}, available: {}",
                    customer.getId(), customer.getName(),
                    customer.getCreditLimit(), customer.getCreditReserved(), customer.availableCredit());

            if (customer.availableCredit().compareTo(event.getOrderTotal()) >= 0) {
                // Reserve credit
                customer.setCreditReserved(customer.getCreditReserved().add(event.getOrderTotal()));
                customerRepository.save(customer);

                log.info(">>> STEP 3: Credit RESERVED for customer {} - new reserved amount: {}",
                        customer.getId(), customer.getCreditReserved());
                result = new CreditReservationResult(event.getOrderId(), true, "Credit reserved");

            } else {
                log.info(">>> STEP 3: INSUFFICIENT credit for customer {} - needed: {}, available: {}",
                        customer.getId(), event.getOrderTotal(), customer.availableCredit());
                result = new CreditReservationResult(event.getOrderId(), false,
                        "Insufficient credit. Available: " + customer.availableCredit()
                                + ", Requested: " + event.getOrderTotal());
            }
        }

        log.info(">>> STEP 4: Publishing credit reservation result: {}", result);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.CREDIT_RESULT_KEY, result);
    }
}
