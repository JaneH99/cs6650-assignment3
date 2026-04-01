package util;

import client.ChatClient;
import client.ConnectionManager;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import model.ClientMessage;

/**
 * MessageSender serves as the "Consumer" in a Producer-Consumer threading model.
 * It is responsible for dequeuing messages from the buffer and dispatching them
 * via established WebSocket connections.
 */

public class MessageSender implements Runnable {

  private final BlockingQueue<ClientMessage> messagesQueue;
  private final CountDownLatch producerLatch;
  private final int taskCount;

  public MessageSender(BlockingQueue<ClientMessage> messagesQueue,
      CountDownLatch producerLatch, int taskCount) {
    this.messagesQueue = messagesQueue;
    this.producerLatch = producerLatch;
    this.taskCount = taskCount;
  }

  //  @Override
  public void run() {
    ConnectionManager connectionManager = ConnectionManager.getInstance();
    for (int i = 0; i < taskCount; i++) {
      try {
        ClientMessage clientMessage = messagesQueue.take();
        String roomId = clientMessage.getRoomId();
        ChatClient chatClient = connectionManager.getConnectionPool().get(roomId);
        if (chatClient != null && chatClient.isOpen()) {
          chatClient.sendMsg(clientMessage);
        }
      } catch (InterruptedException e) {
        System.out.println("Thread Error");
        Thread.currentThread().interrupt();
        break;
      }
    }
    producerLatch.countDown();
  }
}