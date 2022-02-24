/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.CountDownLatch;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class EnhancedClassTaskConfig {

  private final CountDownLatch latch = new CountDownLatch(1);

  @Scheduled(fixedRate = 5000)
  public void run() {
    latch.countDown();
  }

  @Bean
  public CountDownLatch countDownLatch() {
    return latch;
  }
}
