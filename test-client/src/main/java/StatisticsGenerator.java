import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * This class read information from CSV file that contains all latency information and calculate for statistical analysis
 */
public class StatisticsGenerator {
  public static void main(String[] args) {
    String csvFile = args[0];
    List<Long> latencies = new ArrayList<>(500000);
    Map<String, Integer> roomCounts = new HashMap<>();
    Map<String, Integer> typeCounts = new HashMap<>();

    long sum = 0;
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;

    System.out.println("Processing CSV data...");

    try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
      String line = br.readLine(); // Skip header

      while ((line = br.readLine()) != null) {
        // Format: SentTime,MessageType,Latency,StatusCode,RoomId
        String[] values = line.split(",");
        if (values.length < 5) continue;

        // Retrieve info
        String type = values[1];
        long latency = Long.parseLong(values[2]);
        String roomId = values[4];

        latencies.add(latency);
        sum += latency;
        min = Math.min(min, latency);
        max = Math.max(max, latency);

        // Distributions
        typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        roomCounts.put(roomId, roomCounts.getOrDefault(roomId, 0) + 1);
      }
    } catch (IOException e) {
      System.out.println("Error reading CSV: " + e.getMessage());
      return;
    }

    if (latencies.isEmpty()) {
      System.out.println("No data found in CSV.");
      return;
    }

    // Sort for Percentiles and Median
    Collections.sort(latencies);

    // Calculations
    double mean = (double) sum / latencies.size();
    long median = latencies.get(latencies.size() / 2);
    long p95 = latencies.get((int) (latencies.size() * 0.95));
    long p99 = latencies.get((int) (latencies.size() * 0.99));

    // Display Results
    System.out.println("\n===========================================");
    System.out.println("       LOAD TEST PERFORMANCE REPORT        ");
    System.out.println("===========================================");
    System.out.printf("Total Messages Processed: %,d%n", latencies.size());
    System.out.printf("Mean Response Time:       %.2f ms%n", mean);
    System.out.printf("Median Response Time:     %d ms%n", median);
    System.out.printf("95th Percentile (P95):    %d ms%n", p95);
    System.out.printf("99th Percentile (P99):    %d ms%n", p99);
    System.out.printf("Min Response Time:        %d ms%n", min);
    System.out.printf("Max Response Time:        %d ms%n", max);

    System.out.println("\n--- Throughput per Room (Messages Received) ---");
    roomCounts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> System.out.printf("Room %-5s: %d msgs%n", e.getKey(), e.getValue()));

    System.out.println("\n=== Message Type Distribution ===");
    for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
      double percentage = (entry.getValue() * 100.0) / latencies.size();
      System.out.printf("%-10s: %d messages (%.2f%%)%n",
          entry.getKey(), entry.getValue(), percentage);
    }
  }
}