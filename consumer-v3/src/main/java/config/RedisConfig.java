package config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for the Consumer service.
 *
 * Spring Boot auto-configures StringRedisTemplate
 *
 * No additional beans are needed here — the Consumer only publishes to Redis and does not subscribe.
 *
 * StringRedisTemplate (Lettuce) is thread-safe and shared across all
 * consumer channel threads.
 */
@Configuration
public class RedisConfig {
  // Auto-configured by Spring Boot Data Redis starter.
  // StringRedisTemplate bean is available for injection in DeduplicationService
  // and RedisPublisher without explicit declaration.
}
