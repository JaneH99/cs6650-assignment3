package client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import model.LatencyReport;
import util.Metrics;

/**
 * A thread-safe Singleton that manages a pool of WebSocket connections, maintaining a mapping of room identifiers to active clients.
 * It incorporates a heartbeat mechanism to ensure connection persistence and proactive failure detection.
 */
public class ConnectionManager {

  // Static instance of the class
  private static volatile ConnectionManager instance;
  private final Map<String, ChatClient> connectionPool;
  private String serverBaseUri;

  //  Heartbeat mechanism field
  private final ScheduledExecutorService heartbeatScheduler =
      Executors.newSingleThreadScheduledExecutor();
  private static final int HEARTBEAT_INTERVAL_SECONDS = 20;

  // Private constructor to prevent manual instantiation
  private ConnectionManager() {
    this.connectionPool = new ConcurrentHashMap<>();
    startHeartbeat();
  }

  // Public static method to get the single instance
  public static ConnectionManager getInstance() {
    if (instance == null) {
      synchronized (ConnectionManager.class) {
        if (instance == null) {
          instance = new ConnectionManager();
        }
      }
    }
    return instance;
  }

  // Setter for the URI (since constructor is now private)
  public void setServerBaseUri(String serverBaseUri) {
    this.serverBaseUri = serverBaseUri;
  }

  //  Periodically checks all connections in the pool.
  private void startHeartbeat() {
    heartbeatScheduler.scheduleAtFixedRate(() -> {
      if (connectionPool.isEmpty()) return;
      long now = System.currentTimeMillis();
      connectionPool.forEach((roomId, client) -> {
        //        We do not need to send a ping since the client might be trying to connect
        if (client == null || !client.isOpen()) {
          System.err.println("[Heartbeat] ALERT: Room " + roomId + " connection is DOWN.");
        }
        // Check if the connection is Open but silent
        else if (now - client.getLastSeen() > 60_000) {
          System.err.println("[Heartbeat] Room " + roomId + " is a ZOMBIE. Forcing reconnect.");
          client.close();
        } else {
          client.sendPing();
        }
      });
    }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Creates one ChatClient per room and connects them.
   * Blocks until all connections are established (or 30s timeout).
   */
  public void setupConnectionPool(
      CountDownLatch wsConnectedLatch,
      CountDownLatch responseLatch,
      BlockingQueue<LatencyReport> resultsQueue,
      ConcurrentHashMap<String, LatencyReport> pendingMessages,
      int numChatRooms) throws URISyntaxException {

    for (int roomId = 1; roomId <= numChatRooms; roomId++) {
      try {
        String roomIdStr = String.valueOf(roomId);
        // Changes from previous: Fixed per-room identity for load testing — one simulated user per room
        URI uri = new URI(serverBaseUri + roomIdStr
            + "?userId=user-" + roomIdStr + "&username=User" + roomIdStr);
        ChatClient client = new ChatClient(uri, pendingMessages, resultsQueue,
            wsConnectedLatch, responseLatch, roomIdStr);
        client.connect();
        connectionPool.put(roomIdStr, client);
      } catch (Exception e) {
        System.err.println("Failed to connect room " + roomId + ": " + e.getMessage());
      }
    }
    waitForConnections(wsConnectedLatch, numChatRooms);
  }

  private void waitForConnections(CountDownLatch latch, int expectedRooms) {
    try {
      latch.await(30, TimeUnit.SECONDS);
      // Metrics.connections is a global AtomicInteger tracking active sockets
      if (Metrics.connections.get() == expectedRooms) {
        System.out.println("All " + expectedRooms + " rooms connected successfully.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Connection wait interrupted: " + e.getMessage());
    }
  }

  /**
   * Gracefully shuts down all active WebSocket connections in the pool.
   */
  public void shutdownAll() {
    if (connectionPool.isEmpty()) {
      return;
    }
//    Shutdown heartbeat scheduler (force shutdonw)
    heartbeatScheduler.shutdownNow();
//    Shutdown all websocket
    System.out.println("Closing " + connectionPool.size() + " WebSocket connections...");
    for (ChatClient chatClient : connectionPool.values()) {
      chatClient.cleanup();
    }
//    Clear the connectionPool map
    connectionPool.clear();
  }

  public Map<String, ChatClient> getConnectionPool() {
    return connectionPool;
  }
}
