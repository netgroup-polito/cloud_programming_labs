package com.example.ticketservice.controller;

import com.example.ticketservice.config.RabbitConfig;
import com.example.ticketservice.event.DomainEvent;
import com.example.ticketservice.model.Ticket;
import com.example.ticketservice.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    private final TicketRepository ticketRepository;
    private final RabbitTemplate rabbitTemplate;

    public TicketController(TicketRepository ticketRepository, RabbitTemplate rabbitTemplate) {
        this.ticketRepository = ticketRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket createTicket(@RequestBody Ticket ticket) {
        Ticket newTicket = new Ticket(
                ticket.getOrderId(),
                ticket.getItems()
        );
        Ticket savedTicket = ticketRepository.save(newTicket);

        Map<String, Object> data = new HashMap<>();
        data.put("ticketId", savedTicket.getId());
        data.put("orderId", savedTicket.getOrderId());
        data.put("items", savedTicket.getItems());

        DomainEvent event = new DomainEvent(
                savedTicket.getOrderId(),
                "TICKET_CREATED",
                "ticket-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing TICKET_CREATED event for order {}", savedTicket.getOrderId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "ticket.created", event);

        return savedTicket;
    }

    @PutMapping("/{id}/accept")
    public Ticket acceptTicket(@PathVariable Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ticket.setStatus("ACCEPTED");
        Ticket savedTicket = ticketRepository.save(ticket);

        Map<String, Object> data = new HashMap<>();
        data.put("ticketId", savedTicket.getId());
        data.put("orderId", savedTicket.getOrderId());

        DomainEvent event = new DomainEvent(
                savedTicket.getOrderId(),
                "TICKET_ACCEPTED",
                "ticket-service",
                Instant.now().toString(),
                data
        );

        log.info(">>> Publishing TICKET_ACCEPTED event for order {}", savedTicket.getOrderId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "ticket.accepted", event);

        return savedTicket;
    }

    @GetMapping
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @GetMapping("/{id}")
    public Ticket getTicket(@PathVariable Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
