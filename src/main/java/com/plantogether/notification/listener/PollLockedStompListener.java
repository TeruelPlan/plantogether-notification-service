package com.plantogether.notification.listener;

import com.plantogether.common.event.PollLockedEvent;
import com.plantogether.notification.config.RabbitConfig;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code poll.locked} events from RabbitMQ and relays them to connected STOMP clients on
 * {@code /topic/trips/{tripId}/updates}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PollLockedStompListener {

  public static final String MESSAGE_TYPE = "POLL_LOCKED";

  private final SimpMessagingTemplate simpMessagingTemplate;

  @RabbitListener(queues = RabbitConfig.QUEUE_STOMP_POLL_LOCKED)
  public void onPollLocked(PollLockedEvent event) {
    if (event.getTripId() == null) {
      log.warn("Dropping PollLockedEvent with null tripId");
      return;
    }
    PollLockedMessage message =
        new PollLockedMessage(
            MESSAGE_TYPE,
            event.getPollId(),
            event.getTripId(),
            event.getSlotId(),
            event.getStartDate() != null ? event.getStartDate().toString() : null,
            event.getEndDate() != null ? event.getEndDate().toString() : null,
            event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now());
    simpMessagingTemplate.convertAndSend("/topic/trips/" + event.getTripId() + "/updates", message);
  }

  public record PollLockedMessage(
      String type,
      String pollId,
      String tripId,
      String slotId,
      String startDate,
      String endDate,
      Instant occurredAt) {}
}
