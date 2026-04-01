package model;

/**
 * Metadata about a connected WebSocket client.
 */
public class UserInfo {
  private final String userId;
  private final String username;
  private final String sessionId;

  public UserInfo(String userId, String username, String sessionId) {
    this.userId    = userId;
    this.username  = username;
    this.sessionId = sessionId;
  }

  public String getUserId()   { return userId; }
  public String getUsername() { return username; }
  public String getSessionId(){ return sessionId; }
}
