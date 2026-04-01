package util;

/**
 * Utility class for calculating exponential backoff delays.
 * This class provides a helper method to compute retry wait times using
 * an exponential backoff strategy, where the delay increases exponentially
 * with each retry attempt.
 */
public class BackOffUtil {
  private static final int BASE_TIMEOUT = 1000;
  private static final int BACK_OFF_FACTOR = 2;

  public static int calculateExponentialBackoff(int attempt) {
    return (int) (BASE_TIMEOUT * Math.pow(BACK_OFF_FACTOR, attempt));
  }
}
