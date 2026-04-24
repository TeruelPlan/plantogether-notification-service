package com.plantogether.notification.listener;

import com.plantogether.common.event.VoteCastEvent;
import com.plantogether.notification.config.RabbitConfig;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code vote.cast} events (published by destination-service) from RabbitMQ and relays
 * them to connected STOMP clients on {@code /topic/trips/{tripId}/updates}. Pure bridge — no
 * transaction, no gRPC call. Membership is enforced on STOMP SUBSCRIBE by {@code
 * StompMembershipInterceptor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationVoteStompListener {

  public static final String MESSAGE_TYPE = "DESTINATION_VOTE_CAST";

  private final SimpMessagingTemplate simpMessagingTemplate;

  @RabbitListener(queues = RabbitConfig.QUEUE_STOMP_DESTINATION_VOTE_CAST)
  public void onDestinationVoteCast(VoteCastEvent event) {
    if (event.getTripId() == null) {
      log.warn("Dropping VoteCastEvent with null tripId");
      return;
    }
    DestinationVoteCastMessage message =
        new DestinationVoteCastMessage(
            MESSAGE_TYPE,
            event.getTripId(),
            event.getDestinationId(),
            event.getDeviceId(),
            event.getVoteMode(),
            event.getVoteValue(),
            event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now());
    simpMessagingTemplate.convertAndSend("/topic/trips/" + event.getTripId() + "/updates", message);
  }

  public record DestinationVoteCastMessage(
      String type,
      String tripId,
      String destinationId,
      String deviceId,
      String voteMode,
      String voteValue,
      Instant occurredAt) {}
}
