/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.redisson.pubsub.AsyncSemaphore;

public class AsyncSemaphoreAccessor {

  @Nullable private static final Field counterField = findField("counter");
  @Nullable private static final Field listenersField = findField("listeners");

  @Nullable
  public static Supplier<Integer> availableConnectionsSupplier(Object freeConnectionsCounter) {
    if (freeConnectionsCounter instanceof AtomicInteger) {
      return ((AtomicInteger) freeConnectionsCounter)::get;
    }
    if (freeConnectionsCounter instanceof AsyncSemaphore && counterField != null) {
      return () -> readAvailableConnections((AsyncSemaphore) freeConnectionsCounter);
    }
    return null;
  }

  @Nullable
  public static Supplier<Integer> pendingRequestsSupplier(Object freeConnectionsCounter) {
    if (!(freeConnectionsCounter instanceof AsyncSemaphore) || listenersField == null) {
      return null;
    }
    return () -> readPendingRequests((AsyncSemaphore) freeConnectionsCounter);
  }

  @Nullable
  private static Integer readAvailableConnections(AsyncSemaphore semaphore) {
    if (counterField == null) {
      return null;
    }
    try {
      synchronized (semaphore) {
        Object counter = counterField.get(semaphore);
        if (counter instanceof AtomicInteger) {
          return ((AtomicInteger) counter).get();
        }
        if (counter instanceof Number) {
          return ((Number) counter).intValue();
        }
      }
    } catch (IllegalAccessException | RuntimeException ignored) {
      // ignored
    }
    return null;
  }

  @Nullable
  private static Integer readPendingRequests(AsyncSemaphore semaphore) {
    if (listenersField == null) {
      return null;
    }
    try {
      synchronized (semaphore) {
        Object listeners = listenersField.get(semaphore);
        if (listeners instanceof Collection) {
          return ((Collection<?>) listeners).size();
        }
      }
    } catch (IllegalAccessException | RuntimeException ignored) {
      // ignored
    }
    return null;
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
