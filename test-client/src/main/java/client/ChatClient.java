package client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import model.ClientMessage;
import model.LatencyReport;
import util.BackOffUtil;
import util.Metrics;

/**
 * WebSocket client for one chat room. Manages connection lifecycle,
 * heartbeat, and automatic reconnection with exponential backoff.
 *
 *   onMessage() parses a BroadcastMessage instead of a ResponseMessage.
 */
@ClientEndpoint
public class ChatClient {

  private final Gson gson = new Gson();
  private final String roomId;
  private final URI serverUri;
  private Session session;

  // Single-threaded scheduler for reconnect/retry tasks (thread-safe)
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  // Shared resources
  private ConcurrentHashMap<String, LatencyReport> pendingMessages;
  private BlockingQueue<LatencyReport> resultsQueue;
  private CountDownLatch wsConnectedLatch;
  private CountDownLatch responseLatch;

  //  Reconnection fields
  private boolean initialReconnectionEstablished = false;
  private boolean intendedShutDown = false;
  private int reconnectionAttemptCount = 0;
  private final AtomicBoolean reconnecting = new AtomicBoolean(false);

  //  Limit: Allow 5 reconnection attemp
  private static final int MAX_RECONNECTION_ALLOWED = 5;
  //  Limit: Allow 5 resend message attempt
  private static final int MAX_SEND_ALLOWED = 5;
  // Update lastSeen for heartbeat mechanism
  private volatile long lastSeen = System.currentTimeMillis();

  public ChatClient(URI serverUri,
      ConcurrentHashMap<String, LatencyReport> pendingMessages,
      BlockingQueue<LatencyReport> resultsQueue,
      CountDownLatch wsConnectedLatch,
      CountDownLatch responseLatch,
      String roomId) {
    this.serverUri        = serverUri;
    this.pendingMessages  = pendingMessages;
    this.resultsQueue     = resultsQueue;
    this.wsConnectedLatch = wsConnectedLatch;
    this.responseLatch    = responseLatch;
    this.roomId           = roomId;
  }

  /**
   * Triggers the initial WebSocket handshake.
   */
  public void connect() {
    try {
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      // This will trigger the @OnOpen method
      container.connectToServer(this, serverUri);
    } catch (Exception e) {
      // If the initial connection fails, we handle it via the reconnect logic
      System.err.println("Initial connection failed for room " + roomId + ": " + e.getMessage());
      attemptReconnect();
    }
  }

  @OnOpen
  public void onOpen(Session session) {
    this.session = session;
    this.lastSeen = System.currentTimeMillis();
    //    Only increment global metrics on the first successful connection
    this.reconnectionAttemptCount = 0;
    if (!initialReconnectionEstablished) {
      initialReconnectionEstablished = true;
      wsConnectedLatch.countDown();
      Metrics.connections.getAndIncrement();
    }
  }

  /**
   * Handles incoming broadcast messages
   * The messageId field is present in both. Latency correlation is identical.
   * statusCode is set to "SUCCESS" for any received broadcast.
   */
  @OnMessage
  public void onMessage(String message) {
    this.lastSeen = System.currentTimeMillis();
    long receiveTime = System.currentTimeMillis();

    try {
//      Parse Server Response into JSON
      JsonObject json = gson.fromJson(message, JsonObject.class);

      // Keep error-type guard for compatibility
      if (json.has("errorType")) {
        System.err.println("Server Error [" + roomId + "]: " + json.get("errorMessage"));
        return;
      }

      // extract messageId directly from BroadcastMessage JSON
      if (!json.has("messageId")) return;
      String messageId = json.get("messageId").getAsString();

      LatencyReport latencyReport = pendingMessages.remove(messageId);
      if (latencyReport != null) {
        latencyReport.setReceiveTime(receiveTime);
        latencyReport.setStatusCode("SUCCESS");   // all received broadcasts = success
        responseLatch.countDown();
        if (resultsQueue != null) {
          resultsQueue.add(latencyReport);
        }
      }
    } catch (Exception e) {
      System.err.println("Failed to parse message in room " + roomId + ": " + e.getMessage());
    }
  }

  @OnClose
  public void onClose(Session session, CloseReason reason) {
    this.session = null;
//    If the server closes the socket unexpectedly and the maximum reconnection attempts haven’t reached, we will try to reconnect it
//    System.out.println("Connection is closing for legit or not legit reason for room " + roomId);
    if (!intendedShutDown) {
      if (reconnectionAttemptCount < MAX_RECONNECTION_ALLOWED) {
        System.out.println("Unexpected Websocket Closure in room " + roomId + ". Reason: " + reason + ". Attempting to reconnect");
        attemptReconnect();
      } else {
        System.out.println("Maximum reconnection has been reached for room " + roomId + ". Connection will not be retried");
      }
    }
  }

  /**
   * Invoked when an error occurs on the WebSocket connection.
   *
   * This method logs the error details and attempts to reconnect if:
   * - The client did not intentionally shut down the connection, and
   * - The WebSocket is currently closed, and
   * - The maximum number of reconnection attempts has not been reached.
   */

  @OnError
  public void onError(Session session, Throwable throwable) {
    System.err.println("WebSocket Error in room " + roomId + ": " + throwable.getMessage());
    if (!intendedShutDown && (session == null || !session.isOpen())) {
      if (reconnectionAttemptCount < MAX_RECONNECTION_ALLOWED) {
        System.out.println("Websocket Error in room " + roomId + ". Attempting to reconnect");
        attemptReconnect();
      } else {
        System.out.println("Websocket Error in room " + roomId + ". Max reconnection attempt reached for the room room. Connection will not be retried");
      }
    }
  }

  /**
   * Serializes and sends a message.
   * It update the message's timestamp, serializes the message into json and sends the message using the retry mechanism to handle send failures
   * @param msg
   *        The ClientMessage object to be sent.
   */
  public void sendMsg(ClientMessage msg) {
    msg.setTimestamp(Instant.now().toString());
    String json = gson.toJson(msg);
    sendMsgWithRetry(msg, json, 0);
  }

  /**
   * Sends a message through the WebSocket with retry logic and tracks latency.
   *
   * This method handles sending a message, creating a latency report for RTT
   * measurement, and retrying the send in case of failures. It is designed
   * to support performance testing by tracking successful and failed messages.
   */
  public void sendMsgWithRetry(ClientMessage msg, String json, int attempt) {
    if (session == null || !session.isOpen()) {
      retrySend(msg, json, attempt);
      return;
    }
//    Prevent multiple thread access the same session
    synchronized (this.session) {
      try {
        // Standard JSR 356 async send
        session.getAsyncRemote().sendText(json);
        LatencyReport latencyReport = new LatencyReport(msg.getMessageType(), System.currentTimeMillis(),
            msg.getRoomId());
        pendingMessages.put(msg.getMessageId(), latencyReport);
      } catch (Exception e) {
        retrySend(msg, json, attempt);
      }
    }
  }

  /**
   * Schedules a message resend with exponential backoff if the socket is busy or failing.
   */
  private void retrySend(ClientMessage msg, String json, int attempt) {
    int nextAttempt = attempt + 1;
    if (nextAttempt < MAX_SEND_ALLOWED) {
      int waitTime = BackOffUtil.calculateExponentialBackoff(nextAttempt);
      scheduler.schedule(() -> sendMsgWithRetry(msg, json, nextAttempt), waitTime, TimeUnit.MILLISECONDS);
    }
  }


  /**
   * Re-establishes session using serverUri.
   * Uses AtomicBoolean to ensure only one reconnection task is queued at a time.
   * This method is triggered when a connection is closed unexpectedly or an error occurs. It ensures that:
   * 1. Only one reconnection attempt runs at a time using the `reconnecting` flag.
   * 2. Reconnection attempts are counted for metrics (`Metrics.reconnections` and `reconnectionAttemptCount`).
   * 3. The wait time before retrying increases exponentially based on the attempt count.
   */

  private void attemptReconnect() {
    if (!reconnecting.compareAndSet(false, true)) {
      return;
    }
    Metrics.reconnections.getAndIncrement();
    reconnectionAttemptCount++;
    int waitTime = BackOffUtil.calculateExponentialBackoff(reconnectionAttemptCount);
    scheduler.schedule(() -> {
      try {
        connect();
        System.out.println("Reconnection for room"+ roomId + "this is the " + reconnectionAttemptCount + " time try");
      } catch (Exception e) {
        System.out.println("Reconnection failed for " + roomId);
      } finally {
        reconnecting.set(false);
      }
    }, waitTime, TimeUnit.MILLISECONDS);
  }

  public void disableReconnection()  { this.intendedShutDown = true; }

  /**
   * Gracefully shuts down the WebSocket client.
   *
   * - Disables reconnection attempts,
   * - Terminates scheduled tasks,
   * - Closes the WebSocket connection
   */
  public void cleanup() {
    disableReconnection();
    scheduler.shutdown();
    close();
  }

  public void close() {
    try {
      if (this.session != null && this.session.isOpen()) {
        this.session.close();
      }
    } catch (IOException e) {
      System.err.println("Notice: Session for room " + roomId + " did not close cleanly.");
    } finally {
      this.session = null;
    }
  }

  public void setResponseLatch(CountDownLatch newLatch) {
    this.responseLatch = newLatch;
  }


  //  Helper function to get lastSeen timestamp, used by
  public long getLastSeen() {
    return lastSeen;
  }

  /**
   * Heartbeat helper called by ConnectionManager.
   */
  public void sendPing() {
    if (session != null && session.isOpen()) {
      try {
        // Send a standard WebSocket ping frame
        session.getBasicRemote().sendPing(ByteBuffer.wrap(new byte[0]));
      } catch (IOException e) {
        System.err.println("Failed to send ping for room " + roomId);
      }
    }
  }

  //  Update getLastSeen timestamp when PongMessage is echo back from server
  @OnMessage
  public void onPong(PongMessage pongMessage) {
    this.lastSeen = System.currentTimeMillis(); // Heartbeat check
  }

  //  Check status
  public boolean isOpen() {
    return session != null && session.isOpen();
  }
}
