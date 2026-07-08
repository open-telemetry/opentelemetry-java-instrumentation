/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import org.redisson.pubsub.AsyncSemaphore;

public class AsyncSemaphoreAccessor {

  @Nullable private static final Field counterField = findField("counter");
  @Nullable private static final Field listenersField = findField("listeners");

  @Nullable
  public static IntSupplier availableConnectionsSupplier(Object freeConnectionsCounter) {
    if (freeConnectionsCounter instanceof AtomicInteger) {
      return ((AtomicInteger) freeConnectionsCounter)::get;
    }
    if (freeConnectionsCounter instanceof AsyncSemaphore && counterField != null) {
      return () -> readAvailableConnections((AsyncSemaphore) freeConnectionsCounter);
    }
    return null;
  }

  @Nullable
  public static IntSupplier pendingRequestsSupplier(Object freeConnectionsCounter) {
    if (!(freeConnectionsCounter instanceof AsyncSemaphore) || listenersField == null) {
      return null;
    }
    return () -> readPendingRequests((AsyncSemaphore) freeConnectionsCounter);
  }

  private static int readAvailableConnections(AsyncSemaphore semaphore) {
    if (counterField == null) {
      return 0;
    }
    try {
      Object counter = counterField.get(semaphore);
      if (counter instanceof AtomicInteger) {
        return ((AtomicInteger) counter).get();
      }
      if (counter instanceof Number) {
        return ((Number) counter).intValue();
      }
    } catch (IllegalAccessException | RuntimeException ignored) {
      // ignored
    }
    return 0;
  }

  private static int readPendingRequests(AsyncSemaphore semaphore) {
    if (listenersField == null) {
      return 0;
    }
    try {
      Object listeners = listenersField.get(semaphore);
      if (listeners instanceof Collection) {
        return ((Collection<?>) listeners).size();
      }
    } catch (IllegalAccessException | RuntimeException ignored) {
      // ignored
    }
    return 0;
  }

  @Nullable
  private static Field findField(String fieldName) {
    try {
      Field field = AsyncSemaphore.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException | RuntimeException ignored) {
      return null;
    }
  }

  private AsyncSemaphoreAccessor() {}
}
