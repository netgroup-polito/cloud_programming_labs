package com.example.accountingservice.controller;

import com.example.accountingservice.config.RabbitConfig;
import com.example.accountingservice.event.DomainEvent;
import com.example.accountingservice.model.Invoice;
import com.example.accountingservice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceRepository invoiceRepository;
    private final RabbitTemplate rabbitTemplate;

    public InvoiceController(InvoiceRepository invoiceRepository, RabbitTemplate rabbitTemplate) {
        this.invoiceRepository = invoiceRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice createInvoice(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        Invoice invoice = new Invoice(orderId, amount);
        invoice = invoiceRepository.save(invoice);

        Map<String, Object> data = new HashMap<>();
        data.put("invoiceId", invoice.getId());
        data.put("orderId", invoice.getOrderId());
        data.put("amount", invoice.getAmount());

        DomainEvent event = new DomainEvent(
                orderId,
                "INVOICE_CREATED",
                "accounting-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing INVOICE_CREATED event for order {}", orderId);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "accounting.invoiceCreated", event);

        return invoice;
    }

    @GetMapping
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @GetMapping("/{id}")
    public Invoice getInvoice(@PathVariable Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
