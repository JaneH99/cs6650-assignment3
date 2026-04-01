package model;

/**
 * Published to RabbitMQ by the Producer and broadcast to clients by the Producer after receiving from Redis Pub/Sub
 */
public class BroadcastMessage {
  private String messageId;
  private String userId;
  private String username;
  private String message;
  private MessageType messageType;
  private String timestamp;

  private String roomId;
  private String serverId;
  private String clientIp;

  public BroadcastMessage() {}

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getServerId()                   { return serverId; }
  public void   setServerId(String serverId)    { this.serverId = serverId; }
  public String getClientIp()                   { return clientIp; }
  public void   setClientIp(String clientIp)    { this.clientIp = clientIp; }
}
