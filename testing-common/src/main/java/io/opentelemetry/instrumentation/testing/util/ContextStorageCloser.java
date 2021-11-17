/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static org.awaitility.Awaitility.await;

import io.opentelemetry.context.ContextStorage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for closing context storage with retry in case there are strict context check
 * failures.
 */
public final class ContextStorageCloser {

  private ContextStorageCloser() {}

  public static void close(ContextStorage storage) throws Exception {
    if (storage instanceof AutoCloseable) {
      cleanup((AutoCloseable) storage);
    }
  }

  private static void cleanup(AutoCloseable storage) throws Exception {
    ContextRestorer restorer = ContextRestorer.create();
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
        .until(() -> restorer.runWithRestore(storage));
  }

  // Helper class that allows for retrying ContextStorage close operation by restoring
  // ContextStorage to the state where it was before close was called.
  private abstract static class ContextRestorer {
    abstract void restore();

    boolean runWithRestore(AutoCloseable target) {
      try {
        target.close();
        return true;
      } catch (Throwable throwable) {
        restore();
        if (throwable instanceof AssertionError) {
          System.err.println();
          for (Map.Entry<Thread, StackTraceElement[]> threadEntry :
              Thread.getAllStackTraces().entrySet()) {
            System.err.println("Thread " + threadEntry.getKey());
            for (StackTraceElement stackTraceElement : threadEntry.getValue()) {
              System.err.println("\t" + stackTraceElement);
            }
            System.err.println();
          }
          throw (AssertionError) throwable;
        }
        throw new IllegalStateException(throwable);
      }
    }

    static ContextRestorer create()
        throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
      Object strictContextStorage = getStrictContextStorage();
      if (strictContextStorage == null) {
        return null;
      }
      Object pendingScopes = getStrictContextStoragePendingScopes(strictContextStorage);

      Class<?> pendingScopesClass =
          Class.forName(
              "io.opentelemetry.javaagent.shaded.io.opentelemetry.context.StrictContextStorage$PendingScopes");
      Field mapField = pendingScopesClass.getDeclaredField("map");
      mapField.setAccessible(true);
      @SuppressWarnings("unchecked")
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
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
      Class<?> strictContextStorageClass =
          Class.forName(
              "io.opentelemetry.javaagent.shaded.io.opentelemetry.context.StrictContextStorage");
      Field field = strictContextStorageClass.getDeclaredField("pendingScopes");
      field.setAccessible(true);
      return field.get(strictContextStorage);
    }

    private static Object getStrictContextStorage()
        throws NoSuchFieldException, IllegalAccessException {
      Object contextStorage = getAgentContextStorage();
      if (contextStorage == null) {
        return null;
      }
      contextStorage = unwrapStrictContextStressor(contextStorage);
      Class<?> contextStorageClass = contextStorage.getClass();
      if (contextStorageClass.getName().contains("StrictContextStorage")) {
        return contextStorage;
      }
      return null;
    }

    private static Object getAgentContextStorage() {
      try {
        Class<?> contextStorageClass =
            Class.forName(
                "io.opentelemetry.javaagent.shaded.io.opentelemetry.context.ContextStorage");
        Method method = contextStorageClass.getDeclaredMethod("get");
        return method.invoke(null);
      } catch (Exception exception) {
        return null;
      }
    }

    private static Object unwrapStrictContextStressor(Object contextStorage)
        throws NoSuchFieldException, IllegalAccessException {
      Class<?> contextStorageClass = contextStorage.getClass();
      if (contextStorageClass.getName().contains("StrictContextStressor")) {
        Field field = contextStorageClass.getDeclaredField("contextStorage");
        field.setAccessible(true);
        return field.get(contextStorage);
      }
      return contextStorage;
    }
  }
}
