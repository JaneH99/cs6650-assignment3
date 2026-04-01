package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches and logs the full metrics report from the consumer's Metrics API.
 * Called once after the load test completes.
 */
public class MetricsApiClient {

  private final String metricsUrl;

  public MetricsApiClient(String metricsUrl) {
    this.metricsUrl = metricsUrl;
  }

  public void fetchAndLog() {
    System.out.println("\n===========================================");
    System.out.println("Fetching metrics from: " + metricsUrl);
    System.out.println("===========================================");
    try {
      HttpURLConnection conn = (HttpURLConnection) new URL(metricsUrl).openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(10000);

      int status = conn.getResponseCode();
      if (status != 200) {
        System.out.println("[METRICS] HTTP error: " + status);
        return;
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      conn.disconnect();

      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      JsonElement json = JsonParser.parseString(sb.toString());
      System.out.println("[METRICS] Response:\n" + gson.toJson(json));
    } catch (Exception e) {
      System.out.println("[METRICS] Failed to fetch metrics: " + e.getMessage());
    }
  }
}
