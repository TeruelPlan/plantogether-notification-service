package com.plantogether.notification.listener;

import com.plantogether.common.event.PollVoteCastEvent;
import com.plantogether.notification.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes {@code poll.vote.cast} events from RabbitMQ and relays them to
 * connected STOMP clients on {@code /topic/trips/{tripId}/updates}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PollVoteStompListener {

    public static final String MESSAGE_TYPE = "POLL_VOTE_CAST";

    private final SimpMessagingTemplate simpMessagingTemplate;

    @RabbitListener(queues = RabbitConfig.QUEUE_STOMP_POLL_VOTE_CAST)
    public void onPollVoteCast(PollVoteCastEvent event) {
        if (event.getTripId() == null) {
            log.warn("Dropping PollVoteCastEvent with null tripId");
            return;
        }
        PollVoteCastMessage message = new PollVoteCastMessage(
                MESSAGE_TYPE,
                event.getPollId(),
                event.getSlotId(),
                event.getDeviceId(),
                event.getStatus(),
                event.getNewSlotScore(),
                event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now()
        );
        simpMessagingTemplate.convertAndSend(
                "/topic/trips/" + event.getTripId() + "/updates",
                message
        );
    }

    public record PollVoteCastMessage(
            String type,
            String pollId,
            String slotId,
            String deviceId,
            String status,
            int newSlotScore,
            Instant occurredAt
    ) {
    }
}
