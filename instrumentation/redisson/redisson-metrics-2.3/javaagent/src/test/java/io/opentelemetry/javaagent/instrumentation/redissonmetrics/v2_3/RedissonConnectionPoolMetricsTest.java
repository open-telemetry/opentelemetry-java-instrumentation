/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import static io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3.RedissonSingletons.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.javaagent.instrumentation.redissonmetrics.AbstractRedissonConnectionPoolMetricsTest;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.pubsub.AsyncSemaphore;

class RedissonConnectionPoolMetricsTest extends AbstractRedissonConnectionPoolMetricsTest {

  @Override
  protected String instrumentationName() {
    return INSTRUMENTATION_NAME;
  }

  @Override
  protected String serverAddress(String endpoint) {
    String prefixedEndpoint = "redis://" + endpoint;
    try {
      Class<?> uriBuilder = Class.forName("org.redisson.misc.URIBuilder");
      URI uri = (URI) uriBuilder.getMethod("create", String.class).invoke(null, prefixedEndpoint);
      return uri.getScheme() == null ? endpoint : prefixedEndpoint;
    } catch (ClassNotFoundException ignored) {
      return prefixedEndpoint;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected boolean supportsPendingRequests() throws NoSuchFieldException {
    return usesAsyncSemaphore();
  }

  @Override
  protected void assertDynamicPoolMetrics(
      Redisson redisson, String regularPool, String subscriptionPool)
      throws ReflectiveOperationException {
    ClientConnectionsEntry entry = getMasterConnectionsEntry(getMasterSlaveEntry(redisson));
    Object counter = getFreeConnectionsCounter(entry);

    clearMetrics();
    if (counter instanceof AtomicInteger) {
      AtomicInteger permits = (AtomicInteger) counter;
      permits.decrementAndGet();
      try {
        assertUsageMetric(regularPool, 0, 1, subscriptionPool, 0, 0);
      } finally {
        permits.incrementAndGet();
      }
      return;
    }

    AsyncSemaphore semaphore = (AsyncSemaphore) counter;
    acquire(semaphore);
    try {
      assertUsageMetric(regularPool, 0, 1, subscriptionPool, 0, 0);
    } finally {
      semaphore.release();
    }

    assertDynamicPendingRequests(semaphore, regularPool, subscriptionPool);
  }

  @Test
  void shouldReadPendingRequestsAfterListenerRemoval() throws ReflectiveOperationException {
    AsyncSemaphore semaphore = new AsyncSemaphore(0);
    Supplier<Integer> pendingRequests =
        requireNonNull(RedissonConnectionPoolAccessor.pendingRequestsSupplier(semaphore));
    Runnable listener = () -> {};

    assertThat(pendingRequests.get()).isZero();
    semaphore.acquire(listener);
    assertThat(pendingRequests.get()).isEqualTo(1);

    if (!removeQueuedListener(semaphore, listener)) {
      semaphore.release();
    }
    assertThat(pendingRequests.get()).isZero();
  }

  private void assertDynamicPendingRequests(
      AsyncSemaphore semaphore, String regularPool, String subscriptionPool)
      throws ReflectiveOperationException {
    Supplier<Integer> available =
        requireNonNull(RedissonConnectionPoolAccessor.availableConnectionsSupplier(semaphore));
    int permits = requireNonNull(available.get());

    for (int i = 0; i < permits; i++) {
      acquire(semaphore);
    }

    Runnable queued = () -> {};
    clearMetrics();
    semaphore.acquire(queued);
    try {
      assertPendingRequests(regularPool, 1, subscriptionPool, 0);
    } finally {
      if (!removeQueuedListener(semaphore, queued)) {
        semaphore.release();
      }
      for (int i = 0; i < permits; i++) {
        semaphore.release();
      }
    }
  }

  private static Object getFreeConnectionsCounter(ClientConnectionsEntry entry)
      throws ReflectiveOperationException {
    Field field = ClientConnectionsEntry.class.getDeclaredField("freeConnectionsCounter");
    field.setAccessible(true);
    return field.get(entry);
  }

  private static void acquire(AsyncSemaphore semaphore) {
    CompletableFuture<Void> acquired = new CompletableFuture<>();
    semaphore.acquire(() -> acquired.complete(null));
    acquired.join();
  }

  private static boolean removeQueuedListener(AsyncSemaphore semaphore, Runnable listener)
      throws ReflectiveOperationException {
    try {
      Object result =
          semaphore.getClass().getMethod("remove", Runnable.class).invoke(semaphore, listener);
      return !(result instanceof Boolean) || (Boolean) result;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  private static boolean usesAsyncSemaphore() throws NoSuchFieldException {
    return ClientConnectionsEntry.class
        .getDeclaredField("freeConnectionsCounter")
        .getType()
        .getName()
        .equals("org.redisson.pubsub.AsyncSemaphore");
  }
}
