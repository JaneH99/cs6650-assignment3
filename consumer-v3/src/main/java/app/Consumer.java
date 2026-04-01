package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "app", "config", "controller", "db", "model", "mq", "redis", "service", "testing"
})
public class Consumer {
  public static void main(String[] args) {
    SpringApplication.run(Consumer.class, args);
  }
}
