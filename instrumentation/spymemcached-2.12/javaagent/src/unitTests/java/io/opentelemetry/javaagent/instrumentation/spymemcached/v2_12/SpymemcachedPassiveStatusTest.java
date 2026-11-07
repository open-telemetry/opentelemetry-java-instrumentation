/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SpymemcachedPassiveStatusTest {

  private final SpymemcachedAttributesGetter getter = new SpymemcachedAttributesGetter();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void shutdownExecutor() throws InterruptedException {
    executor.shutdownNow();
    assertThat(executor.awaitTermination(1, SECONDS)).isTrue();
  }

  @Test
  void pendingFuturesHaveNoResponseStatusAndExtractionDoesNotWait()
      throws ExecutionException, InterruptedException, TimeoutException {
    CountDownLatch latch = new CountDownLatch(1);
    Object[] futures = {
      new OperationFuture<>("key", latch, 1, executor),
      new GetFuture<>(latch, 1, "key", executor),
      new BulkGetFuture<>(emptyMap(), emptyList(), latch, executor)
    };

    for (Object future : futures) {
      Future<String> result =
          executor.submit(() -> getter.getDbResponseStatusCode(null, future, null));

      assertThat(result.get(1, SECONDS)).isNull();
      assertThat(latch.getCount()).isEqualTo(1);
      if (future instanceof BulkGetFuture) {
        assertThat(((BulkGetFuture<?>) future).isTimeout()).isFalse();
      }
    }
  }

  @Test
  void extractionPreservesAnExistingInterrupt()
      throws ExecutionException, InterruptedException, TimeoutException {
    CountDownLatch latch = new CountDownLatch(1);
    Object[] futures = {
      new OperationFuture<>("key", latch, 1, executor),
      new GetFuture<>(latch, 1, "key", executor),
      new BulkGetFuture<>(emptyMap(), emptyList(), latch, executor)
    };

    for (Object future : futures) {
      Future<String> result =
          executor.submit(
              () -> {
                Thread.currentThread().interrupt();
                try {
                  String statusCode = getter.getDbResponseStatusCode(null, future, null);
                  assertThat(Thread.currentThread().isInterrupted()).isTrue();
                  return statusCode;
                } finally {
                  Thread.interrupted();
                }
              });

      assertThat(result.get(1, SECONDS)).isNull();
      assertThat(latch.getCount()).isEqualTo(1);
      if (future instanceof BulkGetFuture) {
        assertThat(((BulkGetFuture<?>) future).isTimeout()).isFalse();
      }
    }
  }

  @Test
  void cancelledBulkFutureDoesNotReportItsExpectedStatus() {
    CountDownLatch latch = new CountDownLatch(1);
    BulkGetFuture<?> future = new BulkGetFuture<>(emptyMap(), emptyList(), latch, executor);
    future.cancel(false);

    assertThat(getter.getDbResponseStatusCode(null, future, null)).isNull();
    assertThat(getter.getErrorType(null, future, null)).isNull();
    assertThat(future.isCancelled()).isTrue();
    assertThat(future.isTimeout()).isFalse();
    assertThat(latch.getCount()).isEqualTo(1);
  }
}
