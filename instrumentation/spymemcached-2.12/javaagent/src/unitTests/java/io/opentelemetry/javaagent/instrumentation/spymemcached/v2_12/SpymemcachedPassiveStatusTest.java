/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationException;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;
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
      operationFuture(latch),
      getFuture(latch),
      new BulkGetFuture<>(emptyMap(), emptyList(), latch, executor)
    };

    for (Object future : futures) {
      Future<String> result =
          executor.submit(
              () -> {
                assertThat(getter.getErrorType(null, future, null)).isNull();
                return getter.getDbResponseStatusCode(null, future, null);
              });

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
      operationFuture(latch),
      getFuture(latch),
      new BulkGetFuture<>(emptyMap(), emptyList(), latch, executor)
    };

    for (Object future : futures) {
      Future<String> result =
          executor.submit(
              () -> {
                Thread.currentThread().interrupt();
                try {
                  String statusCode = getter.getDbResponseStatusCode(null, future, null);
                  assertThat(getter.getErrorType(null, future, null)).isNull();
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
    BulkGetFuture<?> future =
        new BulkGetFuture<>(emptyMap(), emptyList(), new CountDownLatch(1), executor);
    future.cancel(false);

    assertThat(getter.getDbResponseStatusCode(null, future, null)).isNull();
    assertThat(getter.getErrorType(null, future, null)).isNull();
    assertThat(future.isCancelled()).isTrue();
    assertThat(future.isTimeout()).isFalse();
  }

  @Test
  void errorTypeDoesNotCallPublicResponseStatusGetter() {
    SpymemcachedAttributesGetter getterSpy = spy(getter);
    doReturn(StatusCode.ERR_NOT_FOUND.name())
        .when(getterSpy)
        .getDbResponseStatusCode(null, null, null);

    assertThat(getterSpy.getErrorType(null, null, null)).isNull();
    verify(getterSpy, never()).getDbResponseStatusCode(null, null, null);
  }

  @Test
  void successfulAndPassiveStatusesAreNotClassifiedAsErrors() {
    StatusCode[] statuses = {
      StatusCode.SUCCESS, StatusCode.ERR_NOT_FOUND, StatusCode.ERR_EXISTS, StatusCode.ERR_NOT_STORED
    };

    for (StatusCode statusCode : statuses) {
      OperationStatus status =
          new OperationStatus(statusCode == StatusCode.SUCCESS, statusCode.name(), statusCode);
      GetFuture<Object> future = getFuture(new CountDownLatch(0));
      SpymemcachedSingletons.captureResponseStatus(future, status);

      assertThat(getter.getDbResponseStatusCode(null, future, null)).isNull();
      assertThat(getter.getErrorType(null, future, null)).isNull();
    }
  }

  @Test
  void responseStatusSurvivesResultRetrievalFailureWithoutReplacingErrorType()
      throws InterruptedException {
    OperationStatus status = new OperationStatus(false, "too large", StatusCode.ERR_2BIG);
    GetFuture<Object> future = getFuture(new CountDownLatch(0));
    CompletableFuture<Object> result = new CompletableFuture<>();
    result.completeExceptionally(new IllegalStateException("result retrieval failed"));
    future.set(result, status);
    SpymemcachedSingletons.captureResponseStatus(future, status);

    Throwable retrievalError = catchThrowable(future::get);

    assertThat(retrievalError).isInstanceOf(ExecutionException.class);
    assertThat(retrievalError).hasCauseInstanceOf(IllegalStateException.class);
    assertThat(getter.getDbResponseStatusCode(null, future, retrievalError)).isEqualTo("ERR_2BIG");
    assertThat(getter.getErrorType(null, future, retrievalError))
        .isEqualTo(ExecutionException.class.getName());
    assertThat(getter.getErrorType(null, future, new OperationException())).isEqualTo("ERR_2BIG");
  }

  @Test
  void bulkFutureReportsCapturedErrorStatus() {
    BulkGetFuture<?> future =
        new BulkGetFuture<>(emptyMap(), emptyList(), new CountDownLatch(1), executor);
    OperationStatus status = new OperationStatus(false, "too large", StatusCode.ERR_2BIG);
    SpymemcachedSingletons.captureResponseStatus(future, status);

    assertThat(getter.getDbResponseStatusCode(null, future, null)).isEqualTo("ERR_2BIG");
    assertThat(getter.getErrorType(null, future, null)).isEqualTo("ERR_2BIG");
  }

  private OperationFuture<Boolean> operationFuture(CountDownLatch latch) {
    OperationFuture<Boolean> future = new OperationFuture<>("key", latch, 1, executor);
    future.setOperation(mock(Operation.class));
    return future;
  }

  private GetFuture<Object> getFuture(CountDownLatch latch) {
    GetFuture<Object> future = new GetFuture<>(latch, 1, "key", executor);
    future.setOperation(mock(Operation.class));
    return future;
  }
}
