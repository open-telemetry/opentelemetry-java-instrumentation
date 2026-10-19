/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.redisson.pubsub.AsyncSemaphore;

class RedissonConnectionPoolAccessorTest {

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
}
