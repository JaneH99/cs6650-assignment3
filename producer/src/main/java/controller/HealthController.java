package controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import websocket.SessionRegistry;

/**
 * GET /health — returns active user counts and active room counts
 */
@RestController
public class HealthController {

  private final SessionRegistry sessionRegistry;

  public HealthController(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("service", "producer");
    response.put("activeUsers", sessionRegistry.getActiveUsersCount());
    response.put("activeRooms", sessionRegistry.getRoomCount());
    return response;
  }
}
