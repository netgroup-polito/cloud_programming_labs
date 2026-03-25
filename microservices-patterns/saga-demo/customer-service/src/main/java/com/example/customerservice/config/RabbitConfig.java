package com.example.customerservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.customerservice.event.CreditReservationResult;
import com.example.customerservice.event.OrderCreatedEvent;

import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "saga-exchange";
    public static final String ORDER_CREATED_QUEUE = "order-created-queue";
    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String CREDIT_RESULT_KEY = "credit.result";

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(ORDER_CREATED_QUEUE, true);
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(sagaExchange).with(ORDER_CREATED_KEY);
    }

    @Bean
    public DefaultClassMapper classMapper() {
        DefaultClassMapper mapper = new DefaultClassMapper();
        mapper.setTrustedPackages("*");
        mapper.setIdClassMapping(Map.of(
                "orderCreated", OrderCreatedEvent.class,
                "creditResult", CreditReservationResult.class
        ));
        return mapper;
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(DefaultClassMapper classMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
