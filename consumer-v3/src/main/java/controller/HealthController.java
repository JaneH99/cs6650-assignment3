package controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import service.MessageProcessingService;

/**
 * GET /health — liveness + processing metrics for the Consumer service.
 */
@RestController
public class HealthController {

  private final MessageProcessingService processingService;

  public HealthController(MessageProcessingService processingService) {
    this.processingService = processingService;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("service", "consumer");
    response.put("messagesProcessed", processingService.getProcessed());
    response.put("failures", processingService.getFailures());
    return response;
  }

  @PostMapping("/reset")
  public Map<String, Object> reset() {
    processingService.resetCounters();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "OK");
    response.put("message", "Counters reset to 0");
    return response;
  }
}
