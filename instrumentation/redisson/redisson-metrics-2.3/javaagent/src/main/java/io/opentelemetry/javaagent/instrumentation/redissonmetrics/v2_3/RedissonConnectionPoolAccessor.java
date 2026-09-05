/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.redisson.pubsub.AsyncSemaphore;

class RedissonConnectionPoolAccessor {

  @Nullable private static final Field counterField = findAsyncSemaphoreField("counter");
  @Nullable private static final Method queueSizeMethod;
  @Nullable private static final Field listenersField;

  static {
    Method method = null;
    Field field = null;
    try {
      method = AsyncSemaphore.class.getMethod("queueSize");
    } catch (NoSuchMethodException ignored) {
      // Field fallback is safe only on versions that do not expose queueSize().
      field = findAsyncSemaphoreField("listeners");
    } catch (SecurityException ignored) {
      // ignored
    }
    queueSizeMethod = method;
    listenersField = field;
  }

  @Nullable
  static Supplier<Integer> availableConnectionsSupplier(Object freeConnectionsCounter) {
    if (freeConnectionsCounter instanceof AtomicInteger) {
      return ((AtomicInteger) freeConnectionsCounter)::get;
    }
    if (freeConnectionsCounter instanceof AsyncSemaphore && counterField != null) {
      return () -> readAvailableConnections((AsyncSemaphore) freeConnectionsCounter);
    }
    return null;
  }

  @Nullable
  static Supplier<Integer> pendingRequestsSupplier(Object freeConnectionsCounter) {
    if (!(freeConnectionsCounter instanceof AsyncSemaphore)) {
      return null;
    }
    AsyncSemaphore semaphore = (AsyncSemaphore) freeConnectionsCounter;
    if (queueSizeMethod != null) {
      return () -> readQueueSize(semaphore);
    }
    if (listenersField != null) {
      return () -> readListenersSize(semaphore);
    }
    return null;
  }

  static int getSubscriptionMinimumIdleSize(Object connectionManager, int fallback) {
    try {
      Object config = connectionManager.getClass().getMethod("getConfig").invoke(connectionManager);
      Method getter;
      try {
        getter = config.getClass().getMethod("getSubscriptionConnectionMinimumIdleSize");
      } catch (NoSuchMethodException ignored) {
        getter = config.getClass().getMethod("getSlaveSubscriptionConnectionMinimumIdleSize");
      }
      return ((Number) getter.invoke(config)).intValue();
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      return fallback;
    }
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
    } catch (IllegalAccessException ignored) {
      // ignored
    }
    return null;
  }

  @Nullable
  private static Integer readQueueSize(AsyncSemaphore semaphore) {
    if (queueSizeMethod == null) {
      return null;
    }
    try {
      Object queueSize = queueSizeMethod.invoke(semaphore);
      return queueSize instanceof Number ? ((Number) queueSize).intValue() : null;
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      return null;
    }
  }

  @Nullable
  private static Integer readListenersSize(AsyncSemaphore semaphore) {
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
  private static Field findAsyncSemaphoreField(String fieldName) {
    try {
      Field field = AsyncSemaphore.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException | RuntimeException ignored) {
      return null;
    }
  }

  private RedissonConnectionPoolAccessor() {}
}
