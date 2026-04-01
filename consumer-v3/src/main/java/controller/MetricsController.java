package controller;

import db.MetricsRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metrics API for core queries and analytics queries.
 *
 * Endpoints:
 *   GET /metrics                                          — full report (analytics + total messages sent/wrote to DB)
 *   GET /metrics/room/{roomId}?startTime=&endTime=        — core query 1
 *   GET /metrics/user/{userId}/history?startTime=&endTime= — core query 2
 *   GET /metrics/active-users?startTime=&endTime=         — core query 3
 *   GET /metrics/user/{userId}/rooms                      — core query 4
 *
 * Call GET /metrics from the test client after load test completes.
 */
@RestController
@RequestMapping("/metrics") //base path
public class MetricsController {

  private final MetricsRepository metricsRepository;
  private final org.springframework.cache.CacheManager cacheManager;

  public MetricsController(MetricsRepository metricsRepository,
      org.springframework.cache.CacheManager cacheManager) {
    this.metricsRepository = metricsRepository;
    this.cacheManager = cacheManager;
  }

  /**
   * Full metrics report — called once from client after load test ends.
   * Returns analytics queries + total message count.
   */
  @GetMapping
  public Map<String, Object> getFullMetrics() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("messageStats", metricsRepository.getMessageStats());
    response.put("topUsers", metricsRepository.getTopUsers());
    response.put("topRooms", metricsRepository.getTopRooms());
    response.put("userParticipationPatterns", metricsRepository.getUserParticipationPatterns());
    response.put("totalMessages", metricsRepository.getTotalMessages());
    return response;
  }

  /**
   * Truncates the messages table and clears all caches.
   * Call before each load test to start with a clean slate.
   * Example: DELETE /metrics/reset
   */
  @DeleteMapping("/reset")
  public Map<String, Object> reset() {
    metricsRepository.truncate();
    cacheManager.getCacheNames().forEach(name -> {
      org.springframework.cache.Cache cache = cacheManager.getCache(name);
      if (cache != null) cache.clear();
    });
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "ok");
    response.put("message", "messages table truncated and caches cleared");
    return response;
  }

  /**
   * Core Query 1: Messages for a room in time range.
   * Example: GET /metrics/room/room-1?startTime=...&endTime=...
   */
  @GetMapping("/room/{roomId}")
  public Map<String, Object> getRoomMessages(
      @PathVariable String roomId,
      @RequestParam String startTime,
      @RequestParam String endTime) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("roomId", roomId);
    response.put("startTime", startTime);
    response.put("endTime", endTime);
    response.put("messages", metricsRepository.getRoomMessages(roomId, startTime, endTime));
    return response;
  }

  /**
   * Core Query 2: User message history, optionally filtered by date range.
   * Example: GET /metrics/user/user-1/history
   *          GET /metrics/user/user-1/history?startTime=...&endTime=...
   */
  @GetMapping("/user/{userId}/history")
  public Map<String, Object> getUserHistory(
      @PathVariable String userId,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("userId", userId);
    response.put("messages", metricsRepository.getUserHistory(userId, startTime, endTime));
    return response;
  }

  /**
   * Core Query 3: Count unique active users in a time window.
   * Example: GET /metrics/active-users?startTime=...&endTime=...
   */
  @GetMapping("/active-users")
  public Map<String, Object> getActiveUsers(
      @RequestParam String startTime,
      @RequestParam String endTime) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("startTime", startTime);
    response.put("endTime", endTime);
    response.put("activeUserCount", metricsRepository.countActiveUsers(startTime, endTime));
    return response;
  }

  /**
   * Core Query 4: Rooms a user has participated in with last activity.
   * Example: GET /metrics/user/user-1/rooms
   */
  @GetMapping("/user/{userId}/rooms")
  public Map<String, Object> getUserRooms(@PathVariable String userId) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("userId", userId);
    response.put("rooms", metricsRepository.getRoomsForUser(userId));
    return response;
  }
}
