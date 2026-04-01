package config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import redis.RedisBroadcastListener;

/**
 * Configures Redis Pub/Sub subscription for the Producer.
 *
 * On startup, the Producer subscribes to all rooms (room1 to room20). Since not so many rooms for load test, subscribe to all
 * Will use dynamic subscription for real application
 * When a message arrives on any room channel (published by the Consumer service),
 * RedisBroadcastListener.onMessage() is called and broadcasts to local WebSocket sessions.
 *
 * The RedisMessageListenerContainer manages a dedicated subscriber connection.
 */
@Configuration
public class RedisConfig {

  @Value("${rabbitmq.rooms:20}")
  private int numRooms;

  /**
   * Container that manages the Redis Pub/Sub subscriber connection.
   * Subscribes statically to room 1 to room 20 on startup.
   */
  @Bean
  public RedisMessageListenerContainer redisListenerContainer(
      RedisConnectionFactory connectionFactory,
      RedisBroadcastListener broadcastListener) {

    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);

    // RedisBroadcastListener implements MessageListener directly — add without wrapping
    for (int roomId = 1; roomId <= numRooms; roomId++) {
      container.addMessageListener(broadcastListener, new ChannelTopic("room:" + roomId));
    }

    return container;
  }
}
