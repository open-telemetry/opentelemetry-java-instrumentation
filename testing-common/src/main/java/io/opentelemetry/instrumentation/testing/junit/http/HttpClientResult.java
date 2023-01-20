/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Helper class for capturing result of asynchronous request and running a callback when result is
 * received.
 */
public final class HttpClientResult {
  private static final long timeout = 10_000;
  private final CountDownLatch valueReady = new CountDownLatch(1);
  private final Runnable callback;
  private int status;
  private Throwable throwable;

  public HttpClientResult(Runnable callback) {
    this.callback = callback;
  }

  public void complete(int status) {
    complete(() -> status, null);
  }

  public void complete(Throwable throwable) {
    complete(null, throwable);
  }

  public void complete(Supplier<Integer> status, Throwable throwable) {
    if (throwable != null) {
      this.throwable = throwable;
    } else {
      this.status = status.get();
    }
    callback.run();
    valueReady.countDown();
  }

  public int get() throws Throwable {
    if (!valueReady.await(timeout, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException("Timed out waiting for response in " + timeout + "ms");
    }
    if (throwable != null) {
      throw throwable;
    }
    return status;
  }
}
