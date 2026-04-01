import client.ChatClient;
import client.ConnectionManager;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import model.ClientMessage;
import model.LatencyReport;
import util.CSVWriter;
import util.MetricsApiClient;
import util.MessageGenerator;

/**
 * Endurance test — runs at a sustained rate for a fixed duration.
 * Target rate is 80% of max throughput measured in Test 1.
 * Generates messages on-the-fly (not pre-generated) to avoid memory exhaustion.
 * Logs throughput and success rate every 30 seconds.
 *
 * Usage: java -jar test-client.jar <targetRate> <durationMinutes> <wsUrl> <metricsUrl>
 * Example: java -jar test-client.jar 12000 30 ws://host:8080/chat/ http://host:8081/metrics
 */
public class EnduranceTest {

  public static final int NUM_OF_CHAT_ROOMS = 20;
  public static final int DEFAULT_THREADS = 16;
  public static final int DEFAULT_TARGET_RATE = 10000; // msg/sec — set to 80% of Test 1 result
  public static final int DEFAULT_DURATION_MINUTES = 30;
  public static final String DEFAULT_SERVER_URI = "ws://localhost:8080/chat/";
  public static final String DEFAULT_METRICS_URL = "http://localhost:8081/metrics";

  public static final ConcurrentHashMap<String, LatencyReport> pendingMessages = new ConcurrentHashMap<>();
  // Counts received messages as they are added by ChatClient, before CSVWriter consumes them
  public static final BlockingQueue<LatencyReport> resultsQueue = new LinkedBlockingQueue<>() {
    @Override
    public boolean add(LatencyReport r) {
      if (r != LatencyReport.POISON_PILL) {
        totalReceived.incrementAndGet();
        recentReceived.incrementAndGet();
      }
      return super.add(r);
    }
  };
  public static final ConnectionManager connectionManager = ConnectionManager.getInstance();

  // Counters for periodic reporting
  private static final AtomicLong totalSent = new AtomicLong(0);
  private static final AtomicLong totalReceived = new AtomicLong(0);
  private static final AtomicLong recentSent = new AtomicLong(0);
  private static final AtomicLong recentReceived = new AtomicLong(0);

  public static void main(String[] args) throws Exception {
    int targetRate       = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_TARGET_RATE;
    int durationMinutes  = (args.length > 1) ? Integer.parseInt(args[1]) : DEFAULT_DURATION_MINUTES;
    String serverUri     = (args.length > 2) ? args[2] : DEFAULT_SERVER_URI;
    String metricsUrl    = (args.length > 3) ? args[3] : DEFAULT_METRICS_URL;

    long durationMs = (long) durationMinutes * 60 * 1000;

    System.out.println("===========================================");
    System.out.println("Starting Endurance Test");
    System.out.println("Server:    " + serverUri);
    System.out.println("Rate:      " + targetRate + " msg/sec");
    System.out.println("Duration:  " + durationMinutes + " minutes");
    System.out.println("Threads:   " + DEFAULT_THREADS);
    System.out.println("===========================================\n");

    // ========================= CONNECT =========================
    CountDownLatch wsConnectedLatch = new CountDownLatch(NUM_OF_CHAT_ROOMS);
    CountDownLatch dummyResponseLatch = new CountDownLatch(1); // not used for endurance
    connectionManager.setServerBaseUri(serverUri);
    connectionManager.setupConnectionPool(wsConnectedLatch, dummyResponseLatch,
        resultsQueue, pendingMessages, NUM_OF_CHAT_ROOMS);
    System.out.println("All rooms connected. Starting endurance test...\n");

    // ========================= CSV WRITER =========================
    String outputDir = "results/v3";
    String fileName = "v3_endurance.csv";
    ExecutorService csvExecutor = Executors.newSingleThreadExecutor();
    csvExecutor.submit(new CSVWriter(resultsQueue, outputDir, fileName));

    // ========================= PERIODIC METRICS LOGGER =========================
    ScheduledExecutorService metricsLogger = Executors.newSingleThreadScheduledExecutor();
    long startTime = System.currentTimeMillis();
    metricsLogger.scheduleAtFixedRate(() -> {
      long sent = recentSent.getAndSet(0);
      long received = recentReceived.getAndSet(0);
      long elapsed = (System.currentTimeMillis() - startTime) / 1000;
      System.out.printf("[ENDURANCE] elapsed=%ds sent=%d/sec received=%d/sec totalSent=%d totalReceived=%d%n",
          elapsed, sent / 30, received / 30, totalSent.get(), totalReceived.get());
    }, 30, 30, TimeUnit.SECONDS);

    // ========================= RATE-LIMITED SENDERS =========================
    // Delay per message per thread to achieve target rate
    // Send msgsPerInterval messages per 100ms window per thread, then sleep the remainder.
    // Coarser intervals avoid sub-millisecond parkNanos precision issues on Linux.
    final long intervalNanos = 100_000_000L; // 100ms
    final int msgsPerInterval = Math.max(1, targetRate / DEFAULT_THREADS / 10);

    ExecutorService senderPool = Executors.newFixedThreadPool(DEFAULT_THREADS);
    long testEndTime = startTime + durationMs;

    for (int t = 0; t < DEFAULT_THREADS; t++) {
      senderPool.submit(() -> {
        while (System.currentTimeMillis() < testEndTime) {
          long intervalStart = System.nanoTime();
          for (int i = 0; i < msgsPerInterval && System.currentTimeMillis() < testEndTime; i++) {
            ClientMessage msg = MessageGenerator.generateMessage();
            String roomId = msg.getRoomId();
            ChatClient client = connectionManager.getConnectionPool().get(roomId);
            if (client != null && client.isOpen()) {
              client.sendMsg(msg);
              totalSent.incrementAndGet();
              recentSent.incrementAndGet();
            }
          }
          long remaining = intervalNanos - (System.nanoTime() - intervalStart);
          if (remaining > 0) {
            java.util.concurrent.locks.LockSupport.parkNanos(remaining);
          }
        }
      });
    }

    // ========================= WAIT FOR DURATION =========================
    senderPool.shutdown();
    senderPool.awaitTermination(durationMinutes + 5, TimeUnit.MINUTES);

    // ========================= CLEANUP =========================
    metricsLogger.shutdown();
    resultsQueue.put(LatencyReport.POISON_PILL);
    csvExecutor.shutdown();
    csvExecutor.awaitTermination(30, TimeUnit.SECONDS);
    connectionManager.shutdownAll();

    long totalDurationMs = System.currentTimeMillis() - startTime;

    // ========================= FINAL SUMMARY =========================
    System.out.println("\n=====Endurance Test Completed=====");
    System.out.println("Duration:        " + totalDurationMs / 1000 + "s");
    System.out.println("Total Sent:      " + totalSent.get());
    System.out.println("Total Received:  " + totalReceived.get());
    System.out.printf("Avg Throughput:  %.2f msg/sec%n", totalSent.get() * 1000.0 / totalDurationMs);
    System.out.printf("Success Rate:    %.2f%%%n", totalReceived.get() * 100.0 / totalSent.get());

    // ========================= METRICS API =========================
    System.out.println("\nWaiting 30s for DB writer to finish flushing...");
    Thread.sleep(30_000);
    new MetricsApiClient(metricsUrl).fetchAndLog();
  }
}
