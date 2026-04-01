package util;

import model.ClientMessage;
import java.util.concurrent.*;

/**
 * Orchestrates the execution of a specific load testing phase.
 * Encapsulates thread pool management and synchronization logic.
 */
public class PhaseExecutor {

  /**
   * Executes a phase by distributing message load across a pool of workers.
   * @param numThreads    Concurrent threads to spawn.
   * @param msgCount      Total messages to process in this phase.
   * @param messagesQueue Shared queue containing the message data.
   */
  public void executePhase(int numThreads, int msgCount, BlockingQueue<ClientMessage> messagesQueue) {
    ExecutorService taskExecutor = Executors.newFixedThreadPool(numThreads);
    CountDownLatch producerLatch = new CountDownLatch(numThreads);

    // Distribute load evenly, handling remainders
    int base = msgCount / numThreads;
    int remainder = msgCount % numThreads;

    for (int i = 0; i < numThreads; i++) {
      int msgForThisThread = base + (i < remainder ? 1 : 0);
      taskExecutor.execute(new MessageSender(messagesQueue, producerLatch, msgForThisThread));
    }

    try {
      // Block until all MessageSender threads call producerLatch.countDown()
      producerLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Phase execution interrupted.");
    } finally {
      shutdownExecutor(taskExecutor);
    }
  }

  private void shutdownExecutor(ExecutorService executor) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
