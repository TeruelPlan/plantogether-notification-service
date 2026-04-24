package com.plantogether.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlantogetherNotificationApplication {
  public static void main(String[] args) {
    SpringApplication.run(PlantogetherNotificationApplication.class, args);
  }
}
