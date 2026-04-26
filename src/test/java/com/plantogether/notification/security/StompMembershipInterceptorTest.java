package com.plantogether.notification.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.grpc.TripClient;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class StompMembershipInterceptorTest {

  @Mock TripClient tripClient;
  @Mock MessageChannel channel;

  StompMembershipInterceptor interceptor;
  String tripId;
  String deviceId;

  @BeforeEach
  void setUp() {
    interceptor = new StompMembershipInterceptor(tripClient);
    tripId = UUID.randomUUID().toString();
    deviceId = UUID.randomUUID().toString();
  }

  private Message<byte[]> subscribe(String destination) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    accessor.setUser(
        new UsernamePasswordAuthenticationToken(deviceId, null, Collections.emptyList()));
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  @Test
  void subscribe_member_permitsMessage() {
    when(tripClient.isMember(tripId, deviceId)).thenReturn(true);

    Message<?> result =
        interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel);

    assertNotNull(result);
    verify(tripClient, times(1)).isMember(tripId, deviceId);
  }

  @Test
  void subscribe_nonMember_throwsAccessDenied() {
    when(tripClient.isMember(tripId, deviceId)).thenReturn(false);

    assertThrows(
        AccessDeniedException.class,
        () -> interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel));
  }

  @Test
  void subscribe_malformedDestination_passesThrough() {
    Message<?> result = interceptor.preSend(subscribe("/topic/system/metrics"), channel);

    assertNotNull(result);
    verifyNoInteractions(tripClient);
  }

  @Test
  void subscribe_cacheHit_doesNotCallGrpc() {
    when(tripClient.isMember(tripId, deviceId)).thenReturn(true);

    interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel);
    interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel);

    verify(tripClient, times(1)).isMember(tripId, deviceId);
  }

  @Test
  void subscribe_missingPrincipal_throwsAccessDenied() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination("/topic/trips/" + tripId + "/updates");
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
  }
}
