package util;

import java.util.concurrent.BlockingQueue;
import model.ClientMessage;

/**
 * A runnable task that generates a batch of client messages and enqueues them into a shared BlockingQueue
 */
public class BatchMessageGenerator implements Runnable{

  private final BlockingQueue<ClientMessage> messagesQueue;
  private final int numMessages;

  public BatchMessageGenerator(BlockingQueue<ClientMessage> messagesQueue, int numMessages) {
    this.messagesQueue = messagesQueue;
    this.numMessages = numMessages;
  }

  @Override
  public void run() {
    for (int i = 0; i < numMessages; i++) {
      try {
        ClientMessage clientMessage = MessageGenerator.generateMessage();
        messagesQueue.add(clientMessage);
      } catch (Exception e) {
        System.err.println("Error generating messages: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }
}
