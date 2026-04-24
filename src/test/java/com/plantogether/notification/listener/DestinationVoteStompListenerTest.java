package com.plantogether.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plantogether.common.event.VoteCastEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class DestinationVoteStompListenerTest {

  @Mock private SimpMessagingTemplate simpMessagingTemplate;

  private DestinationVoteStompListener listener;

  @BeforeEach
  void setUp() {
    listener = new DestinationVoteStompListener(simpMessagingTemplate);
  }

  @Test
  @DisplayName("onDestinationVoteCast relays frame to the trip topic")
  void onDestinationVoteCast_relaysFrameToTripTopic() {
    String tripId = UUID.randomUUID().toString();
    String destinationId = UUID.randomUUID().toString();
    String deviceId = UUID.randomUUID().toString();
    Instant occurredAt = Instant.parse("2026-04-24T10:15:30Z");

    VoteCastEvent event =
        VoteCastEvent.builder()
            .tripId(tripId)
            .destinationId(destinationId)
            .deviceId(deviceId)
            .voteMode("APPROVAL")
            .voteValue("YES")
            .occurredAt(occurredAt)
            .build();

    listener.onDestinationVoteCast(event);

    ArgumentCaptor<DestinationVoteStompListener.DestinationVoteCastMessage> captor =
        ArgumentCaptor.forClass(DestinationVoteStompListener.DestinationVoteCastMessage.class);
    verify(simpMessagingTemplate)
        .convertAndSend(eq("/topic/trips/" + tripId + "/updates"), captor.capture());

    DestinationVoteStompListener.DestinationVoteCastMessage sent = captor.getValue();
    assertThat(sent.type()).isEqualTo("DESTINATION_VOTE_CAST");
    assertThat(sent.tripId()).isEqualTo(tripId);
    assertThat(sent.destinationId()).isEqualTo(destinationId);
    assertThat(sent.deviceId()).isEqualTo(deviceId);
    assertThat(sent.voteMode()).isEqualTo("APPROVAL");
    assertThat(sent.voteValue()).isEqualTo("YES");
    assertThat(sent.occurredAt()).isEqualTo(occurredAt);
  }

  @Test
  @DisplayName("onDestinationVoteCast drops events with null tripId")
  void onDestinationVoteCast_nullTripId_isDropped() {
    VoteCastEvent event =
        VoteCastEvent.builder()
            .tripId(null)
            .destinationId(UUID.randomUUID().toString())
            .deviceId(UUID.randomUUID().toString())
            .voteMode("SIMPLE")
            .voteValue("YES")
            .occurredAt(Instant.now())
            .build();

    listener.onDestinationVoteCast(event);

    verify(simpMessagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
  }

  @Test
  @DisplayName("onDestinationVoteCast defaults occurredAt to now when null")
  void onDestinationVoteCast_nullOccurredAt_defaultsToNow() {
    String tripId = UUID.randomUUID().toString();
    VoteCastEvent event =
        VoteCastEvent.builder()
            .tripId(tripId)
            .destinationId(UUID.randomUUID().toString())
            .deviceId(UUID.randomUUID().toString())
            .voteMode("RANKING")
            .voteValue("1")
            .occurredAt(null)
            .build();

    Instant before = Instant.now();
    listener.onDestinationVoteCast(event);

    ArgumentCaptor<DestinationVoteStompListener.DestinationVoteCastMessage> captor =
        ArgumentCaptor.forClass(DestinationVoteStompListener.DestinationVoteCastMessage.class);
    verify(simpMessagingTemplate)
        .convertAndSend(eq("/topic/trips/" + tripId + "/updates"), captor.capture());

    Instant occurredAt = captor.getValue().occurredAt();
    assertThat(occurredAt).isNotNull();
    assertThat(occurredAt)
        .isBetween(before.minusSeconds(1), Instant.now().plus(2, ChronoUnit.SECONDS));
  }
}
