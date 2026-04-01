package util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe metrics holder for tracking system-level counters.
 */
public final class Metrics {
  public static final AtomicInteger connections = new AtomicInteger(0);
  public static final AtomicInteger reconnections = new AtomicInteger(0);
}
