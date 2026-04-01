package util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.ClientMessage;
import model.MessageType;

/**
 * Utility class for generating randomized {@link ClientMessage} instances.
 *
 * This class is primarily used for testing, simulation, and load-generation
 * scenarios. It produces client messages with randomized users, rooms,
 * timestamps, and message types.
 *
 * A fixed pool of 50 pre-defined message strings is created once and reused
 * to reduce allocation overhead.
 */

public class MessageGenerator {

  private static final List<String> randomMsg = generateRandomMsg();

  /**
   * Generate 50 pre-defined message
   * @return a list of pre-defined message
   */
  private static List<String> generateRandomMsg() {
    List<String> res = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      res.add("Message " + i);
    }
    return res;
  }

  private static final Random r = new Random();

  /**
   * Random Client Message generator
   * @return an instance of ClientMessage
   */
  public static ClientMessage generateMessage() {
    String userId = String.valueOf(r.nextInt(100000) + 1);
    String username = "user" + userId;
    String roomId = String.valueOf(r.nextInt(20) + 1);
    String message = randomMsg.get(r.nextInt(50));
    String timestamp = Instant.now().toString();
    int msgTypePicker = r.nextInt(100);
    MessageType messageType;
    if (msgTypePicker <= 90) {
      messageType = MessageType.TEXT;
    } else if (msgTypePicker <= 95) {
      messageType = MessageType.JOIN;
    } else {
      messageType = MessageType.LEAVE;
    }
    return new ClientMessage(userId, username, message, timestamp, messageType, roomId);
  }
}
