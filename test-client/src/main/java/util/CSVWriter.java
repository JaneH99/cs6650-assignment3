package util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import model.LatencyReport;

/**
 * A runnable consumer that writes {@link LatencyReport} records from a BlockingQueue to a csv file
 * This class is designed for producer–consumer workflows where latency
 * measurement results are generated asynchronously and persisted to disk.
 * It continuously consumes records from the queue and writes them in CSV
 * format until a POISON_PILL sentinel value is encountered.
 */

public class CSVWriter implements Runnable{

  private final BlockingQueue<LatencyReport> resultsQueue;
  private final File outputFile;
  private static final int BATCH_SIZE = 1000;

  public CSVWriter(BlockingQueue<LatencyReport> resultsQueue, String outputDir, String fileName) {
    this.resultsQueue = resultsQueue;
    File dir = new File(outputDir);
    if (!dir.exists()) {
      boolean created = dir.mkdirs();
      if (!created) {
        System.err.println("CRITICAL: Could not create " + dir.getAbsolutePath());
      }
    }
    this.outputFile = new File(dir, fileName);
  }

  @Override
  public void run() {
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
        PrintWriter writer = new PrintWriter(bw)) {
      writer.println("timestamp,messageType,latency,statusCode,roomId");

      StringBuilder batch = new StringBuilder();
      int written = 0;

      while (true) {
        LatencyReport record = resultsQueue.take();

        if (record == LatencyReport.POISON_PILL) break;

        batch.append(record.toCSV()).append("\n");
        written++;

        // Flush in batches
        if (written % BATCH_SIZE == 0) {
          writer.print(batch.toString());
          batch.setLength(0); // Clear batch
        }
      }

      // Flush remaining
      if (batch.length() > 0) {
        writer.print(batch.toString());
      }

      System.out.println("CSV writing complete: " + written + " records written to " + outputFile.getAbsolutePath());

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println("CSV writer interrupted, exiting early");
    } catch (IOException e) {
      System.err.println("Error writing CSV: " + e.getMessage());
      e.printStackTrace();
    }
  }
}