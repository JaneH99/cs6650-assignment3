package websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import service.MessagePublisher;

/**
 * This class is a Spring-managed singleton bean and is initialized at application startup.
 * Bidirectional WebSocket handler for the Producer service.
 *
 * ON OPEN: register session in SessionRegistry
 * ON MESSAGE: delegate to MessagePublisher to publish message to RabbitMQ queue
 * ON CLOSE: unregister session from SessionRegistry
 *
 * Broadcast path (Redis → Producer → client) is handled separately by
 * RedisBroadcastListener calling SessionRegistry.broadcast().
 *
 * WebSocket URL: ws://host:8080/chat/{roomId}?userId=X&username=Y
 * Fixed per-room identity for load testing — one simulated user per room, so for above, userId is roomId and userName is user-roomId
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final SessionRegistry sessionRegistry;
  private final MessagePublisher messagePublisher;

  public ChatWebSocketHandler(SessionRegistry sessionRegistry, MessagePublisher messagePublisher) {
    this.sessionRegistry  = sessionRegistry;
    this.messagePublisher = messagePublisher;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String roomId = extractRoomId(session);
    String userId = extractQueryParam(session, "userId");
    String username = extractQueryParam(session, "username");
    if (userId == null)   userId = session.getId();
    if (username == null) username = "user-" + session.getId().substring(0, 6);

    sessionRegistry.register(roomId, session, userId, username);
    log.info("Client connected: sessionId={} room={} user={}", session.getId(), roomId, userId);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String roomId = extractRoomId(session);
    try {
      messagePublisher.publish(roomId, session, message.getPayload());
    } catch (Exception e) {
      log.error("Failed to publish message: sessionId={} room={}", session.getId(), roomId, e);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = extractRoomId(session);
    sessionRegistry.unregister(roomId, session);
    log.info("Client disconnected: sessionId={} room={} status={}", session.getId(), roomId, status);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.warn("Transport error: sessionId={} error={}", session.getId(), exception.getMessage());
  }

  // ---- Helper functions ----

  private String extractRoomId(WebSocketSession session) {
    String path = session.getUri() != null ? session.getUri().getPath() : "";
    String[] parts = path.split("/");
    return parts.length > 0 ? parts[parts.length - 1] : "unknown";
  }

//  This function will be used in real application. For current load-test, will use default username and userId
  private String extractQueryParam(WebSocketSession session, String key) {
    String query = session.getUri() != null ? session.getUri().getQuery() : null;
    if (query == null) return null;
    for (String param : query.split("&")) {
      String[] kv = param.split("=", 2);
      if (kv.length == 2 && kv[0].equals(key)) return kv[1];
    }
    return null;
  }
}
