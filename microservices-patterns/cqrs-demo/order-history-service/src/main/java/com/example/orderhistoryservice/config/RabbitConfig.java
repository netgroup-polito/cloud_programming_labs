package com.example.orderhistoryservice.config;

import com.example.orderhistoryservice.event.DomainEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "cqrs-events";
    public static final String QUEUE = "order-history-queue";

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue orderHistoryQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding binding(Queue orderHistoryQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(orderHistoryQueue).to(topicExchange).with("#");
    }

    @Bean
    public DefaultClassMapper classMapper() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("*");
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put("domainEvent", DomainEvent.class);
        classMapper.setIdClassMapping(idClassMapping);
        return classMapper;
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
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
