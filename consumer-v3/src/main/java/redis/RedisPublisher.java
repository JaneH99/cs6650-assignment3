package redis;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import model.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes processed messages to Redis Pub/Sub channel room:{roomId}.
 *
 * A Resilience4J circuit breaker protects against Redis unavailability:
 *   - CLOSED  : messages published normally
 *   - OPEN    : fallback throws RuntimeException → caller NACKs to RabbitMQ
 *               (message stays in queue until Redis recovers)
 *   - HALF-OPEN: 3 test calls to probe Redis health
 *
 * All Producer instances subscribed to room:{roomId} receive the published
 * message and broadcast it to their locally-connected WebSocket clients.
 *
 */
@Service
public class RedisPublisher {

  private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);

  private final StringRedisTemplate redisTemplate;

  public RedisPublisher(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Publishes all messages for one room as a single JSON array payload.
   * Reduces Redis round-trips from N (one per message) to 1 (one per room per flush).
   * The Producer's RedisBroadcastListener parses the array and broadcasts each
   * message individually to WebSocket clients.
   */
  @CircuitBreaker(name = "redis-publish", fallbackMethod = "publishRoomBatchFallback")
  public void publishRoomBatch(String roomId, List<String> messageJsons) {
    String payload = "[" + String.join(",", messageJsons) + "]";
    redisTemplate.convertAndSend("room:" + roomId, payload);
    log.debug("Published {} messages to room:{} as one payload", messageJsons.size(), roomId);
  }

  public void publishRoomBatchFallback(String roomId, List<String> messageJsons, Exception e) {
    log.warn("Redis circuit open — room:{} batch of {} dropped: {}", roomId, messageJsons.size(), e.getMessage());
    throw new RuntimeException("Redis room batch publish circuit open for room:" + roomId, e);
  }
}
