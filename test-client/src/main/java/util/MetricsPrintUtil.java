package util;

/**
 * This helper class print out performance matrix
 */
public class MetricsPrintUtil {
  public static void printPhaseMetrics(
      String phaseName,
      int messagesSent,
      int successfulMessages,
      int failedMessages,
      long durationMs,
      int threadsCount) {
    System.out.println("\n=== " + phaseName + " Performance Metrics With " + threadsCount + " Threads ===");
    System.out.println("Messages sent: " + messagesSent);
    System.out.println("Successful Message: " + successfulMessages);
    System.out.println("Failed Messages: " + failedMessages);
    System.out.println("Duration: " + durationMs + " ms");
    System.out.println("Throughput: " + String.format("%.2f", (successfulMessages / (durationMs / 1000.0))) + " msg/sec");
    System.out.println("Success rate: " + String.format("%.2f", (successfulMessages * 100.0 / messagesSent)) + "%");
  }
}