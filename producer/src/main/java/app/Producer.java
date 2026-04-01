package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "app", "config", "controller", "model", "mq", "redis", "service", "websocket"
})
public class Producer {
  public static void main(String[] args) {
    SpringApplication.run(Producer.class, args);
  }
}
