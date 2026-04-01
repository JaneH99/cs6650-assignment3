package redis;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import model.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import websocket.SessionRegistry;

/**
 * Listens on Redis Pub/Sub channels room:1 … room:20 (subscribed statically at startup).
 * It should be changed to dynamic subscription when the number of rooms changes dramatically for future assignment
 *
 * When a message arrives from Redis (published by the Consumer service after pulling
 * from RabbitMQ), this listener calls SessionRegistry.broadcast() to push the message
 * to all locally-connected WebSocket clients in that room.
 *
 * The RedisMessageListenerContainer (in RedisConfig) dispatches onMessage() on a
 * dedicated thread pool, so this method must be thread-safe — it is, because
 * SessionRegistry.broadcast() synchronizes per WebSocketSession.
 */
@Component
public class RedisBroadcastListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(RedisBroadcastListener.class);

  private final SessionRegistry sessionRegistry;
  private final Gson gson = new Gson();

  public RedisBroadcastListener(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String json = new String(message.getBody(), StandardCharsets.UTF_8).trim();

      if (json.startsWith("[")) {
        // Batched payload: JSON array of BroadcastMessage objects published by consumer
        BroadcastMessage[] msgs = gson.fromJson(json, BroadcastMessage[].class);
        for (BroadcastMessage msg : msgs) {
          if (msg == null || msg.getRoomId() == null) {
            log.warn("Malformed message in Redis batch, skipping");
            continue;
          }
          log.debug("Redis batch message: messageId={} room={}", msg.getMessageId(), msg.getRoomId());
          sessionRegistry.broadcast(msg.getRoomId(), gson.toJson(msg));
        }
      } else {
        // Single message (legacy / single-message publish path)
        BroadcastMessage msg = gson.fromJson(json, BroadcastMessage.class);
        if (msg == null || msg.getRoomId() == null) {
          log.warn("Received malformed Redis message, skipping");
          return;
        }
        log.debug("Redis message received: messageId={} room={}", msg.getMessageId(), msg.getRoomId());
        sessionRegistry.broadcast(msg.getRoomId(), json);
      }
    } catch (Exception e) {
      log.error("Error processing Redis broadcast message", e);
    }
  }
}
