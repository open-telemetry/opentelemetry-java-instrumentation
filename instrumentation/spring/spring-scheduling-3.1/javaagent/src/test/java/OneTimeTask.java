/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class OneTimeTask implements Runnable {

  private final CountDownLatch latch = new CountDownLatch(1);

  @Override
  public void run() {
    latch.countDown();
  }

  public void blockUntilExecute() throws InterruptedException {
    latch.await(5, TimeUnit.SECONDS);
  }
}
