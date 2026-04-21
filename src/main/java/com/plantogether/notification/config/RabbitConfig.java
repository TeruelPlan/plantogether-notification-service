package com.plantogether.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for notification-service. Declares queues this service consumes
 * from the shared {@code plantogether.events} topic exchange.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "plantogether.events";

    public static final String QUEUE_STOMP_POLL_VOTE_CAST = "q.notification.stomp.poll.vote.cast";
    public static final String QUEUE_STOMP_POLL_LOCKED = "q.notification.stomp.poll.locked";

    public static final String ROUTING_KEY_POLL_VOTE_CAST = "poll.vote.cast";
    public static final String ROUTING_KEY_POLL_LOCKED = "poll.locked";

    @Bean
    public TopicExchange plantogetherExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue stompPollVoteCastQueue() {
        return new Queue(QUEUE_STOMP_POLL_VOTE_CAST, true);
    }

    @Bean
    public Queue stompPollLockedQueue() {
        return new Queue(QUEUE_STOMP_POLL_LOCKED, true);
    }

    @Bean
    public Binding stompPollVoteCastBinding(Queue stompPollVoteCastQueue, TopicExchange plantogetherExchange) {
        return BindingBuilder.bind(stompPollVoteCastQueue)
                .to(plantogetherExchange)
                .with(ROUTING_KEY_POLL_VOTE_CAST);
    }

    @Bean
    public Binding stompPollLockedBinding(Queue stompPollLockedQueue, TopicExchange plantogetherExchange) {
        return BindingBuilder.bind(stompPollLockedQueue)
                .to(plantogetherExchange)
                .with(ROUTING_KEY_POLL_LOCKED);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
