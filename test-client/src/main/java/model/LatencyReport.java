package model;

/**
 * Represents a latency report for a message, containing all the information
 * needed to write a record to a CSV file.
 *
 * Includes details such as message type, sent and received timestamps,
 * room ID, status code, and computed latency.
 */
public class LatencyReport {
  private MessageType messageType;
  private long sentTime;
  private long receiveTime;
  private String statusCode;
  private String roomId;
  private long latency;

  public LatencyReport(MessageType messageType, long sentTime, String roomId) {
    this.messageType = messageType;
    this.sentTime = sentTime;
    this.roomId = roomId;
  }


  public MessageType getMessageType() {
    return messageType;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  public long getSentTime() {
    return sentTime;
  }

  public void setSentTime(long sentTime) {
    this.sentTime = sentTime;
  }

  public long getReceiveTime() {
    return receiveTime;
  }

  public void setReceiveTime(long receiveTime) {
    this.receiveTime = receiveTime;
    this.latency = receiveTime - sentTime;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  //  We do not have setter for latency, it is calculated when message is received
  public long getLatency() {
    return latency;
  }

  //  Write to csv
  public String toCSV() {
    return String.format("%d,%s,%d,%s,%s",
        sentTime, messageType.toString(), latency, statusCode, roomId);
  }

  public static final LatencyReport POISON_PILL =
      new LatencyReport(null, -1L, null);
}
