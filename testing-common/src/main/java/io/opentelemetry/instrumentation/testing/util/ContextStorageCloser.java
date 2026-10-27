/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static org.awaitility.Awaitility.await;

import io.opentelemetry.context.ContextStorage;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * Helper class for closing context storage with retry in case there are strict context check
 * failures.
 */
public class ContextStorageCloser {

  private ContextStorageCloser() {}

  public static void close(ContextStorage storage) throws Exception {
    close(storage, storage);
  }

  /**
   * Closes storage with leak-check retries using the supplied underlying strict storage, which may
   * live in a different class loader.
   */
  public static void close(ContextStorage storage, @Nullable Object strictContextStorage)
      throws Exception {
    if (storage instanceof AutoCloseable) {
      cleanup((AutoCloseable) storage, strictContextStorage);
    }
  }

  private static void cleanup(AutoCloseable storage, @Nullable Object strictContextStorage)
      throws Exception {
    ContextRestorer restorer = ContextRestorer.create(strictContextStorage);
    if (restorer == null) {
      storage.close();
      return;
    }

    // If close is called before all request processing threads have completed we might get false
    // positive notifications for leaked scopes when strict context checks are enabled. Here we
    // retry close when scope leak was reported.
    await()
        .ignoreException(AssertionError.class)
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(1))
        .pollDelay(Duration.ZERO)
        .until(() -> restorer.runWithRestore(storage));
  }

  // Helper class that allows for retrying ContextStorage close operation by restoring
  // ContextStorage to the state where it was before close was called.
  private abstract static class ContextRestorer {
    abstract void restore();

    @SuppressWarnings("SystemOut") // only used for debugging tests
    boolean runWithRestore(AutoCloseable target) {
      try {
        target.close();
        return true;
      } catch (Throwable t) {
        restore();
        if (t instanceof AssertionError) {
          System.err.println("AssertionError " + t);
          for (StackTraceElement stackTraceElement : t.getStackTrace()) {
            System.err.println("\t" + stackTraceElement);
          }
          for (Map.Entry<Thread, StackTraceElement[]> threadEntry :
              Thread.getAllStackTraces().entrySet()) {
            System.err.println("Thread " + threadEntry.getKey());
            for (StackTraceElement stackTraceElement : threadEntry.getValue()) {
              System.err.println("\t" + stackTraceElement);
            }
            System.err.println();
          }
          throw (AssertionError) t;
        }
        throw new IllegalStateException(t);
      }
    }

    @Nullable
    static ContextRestorer create(@Nullable Object strictContextStorage)
        throws NoSuchFieldException, IllegalAccessException {
      if (strictContextStorage == null
          || !strictContextStorage.getClass().getName().contains("StrictContextStorage")) {
        return null;
      }

      Object pendingScopes = getStrictContextStoragePendingScopes(strictContextStorage);
      Field mapField = pendingScopes.getClass().getDeclaredField("map");
      mapField.setAccessible(true);
      @SuppressWarnings("unchecked") // casting reflection result
      ConcurrentHashMap<Object, Object> map =
          (ConcurrentHashMap<Object, Object>) mapField.get(pendingScopes);
      Map<Object, Object> copy = new HashMap<>(map);

      return new ContextRestorer() {
        @Override
        void restore() {
          map.putAll(copy);
        }
      };
    }

    private static Object getStrictContextStoragePendingScopes(Object strictContextStorage)
        throws NoSuchFieldException, IllegalAccessException {
      Field field = strictContextStorage.getClass().getDeclaredField("pendingScopes");
      field.setAccessible(true);
      return field.get(strictContextStorage);
    }

  }
}
