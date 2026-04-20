/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CountDownLatch;
import org.springframework.stereotype.Component;

@Component
public class OneTimeTask implements Runnable {

  private final CountDownLatch latch = new CountDownLatch(1);

  @Override
  public void run() {
    latch.countDown();
  }

  public void blockUntilExecute() throws InterruptedException {
    latch.await(5, SECONDS);
  }
}
