/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public final class ThreadPoolExecutorMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors";
  private static final String UNKNOWN = "unknown";
  private static final String THREAD_NAME_NORMALIZATION =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "executors")
          .getString("name_normalization", "all");
  private static final Pattern TRAILING_DIGITS_PATTERN = Pattern.compile("\\d+$");
  private static final Pattern ALL_DIGITS_PATTERN = Pattern.compile("\\d+");

  private static final Cache<Executor, Registration> registrations = Cache.weak();

  public static void preRegister(ThreadPoolExecutor executor) {
    preRegister(executor, emptySet());
  }

  public static void preRegister(Executor executor, Set<Thread> threads) {
    registrations.computeIfAbsent(executor, ignored -> new Registration(threads));
  }

  public static void reregister(
      Executor executor, @Nullable String ownerName, String threadNameNormalization) {
    String executorOwnerName = ownerName == null ? UNKNOWN : ownerName;
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.reregister(executor, executorOwnerName, threadNameNormalization);
    }
  }

  public static void onThreadFactoryChanged(Executor executor) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.awaitNextWorkerThread();
    }
  }

  public static void onWorkerThreadStarted(Executor executor, Thread thread) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.onWorkerThreadStarted(executor, thread);
    }
  }

  public static void recordRejectedTask(Executor executor) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.recordRejectedTask();
    }
  }

  public static void unregister(Executor executor) {
    Registration registration;
    synchronized (registrations) {
      registration = registrations.get(executor);
      if (registration != null) {
        registrations.remove(executor);
      }
    }
    if (registration != null) {
      registration.close();
    }
  }

  private static AtomicReference<BatchCallback> createBatchCallback(
      JvmExecutorMetrics metrics, ThreadPoolExecutor executor) {
    ObservableLongMeasurement threadCount = metrics.threadCount();
    ObservableLongMeasurement coreThreads = metrics.coreThreads();
    ObservableLongMeasurement maxThreads = metrics.maxThreads();
    ObservableLongMeasurement queueSize = metrics.queueSize();
    ObservableLongMeasurement queueRemaining = metrics.queueRemaining();
    ObservableLongMeasurement completedTasks = metrics.completedTasks();

    WeakReference<ThreadPoolExecutor> executorRef = new WeakReference<>(executor);
    AtomicReference<BatchCallback> callbackRef = new AtomicReference<>();
    callbackRef.set(
        metrics.batchCallback(
            () -> {
              ThreadPoolExecutor threadPoolExecutor = executorRef.get();
              if (threadPoolExecutor == null) {
                closeCallback(callbackRef);
                return;
              }

              long active = threadPoolExecutor.getActiveCount();
              threadCount.record(active, metrics.activeThreadAttributes());
              threadCount.record(
                  Math.max(threadPoolExecutor.getPoolSize() - active, 0),
                  metrics.idleThreadAttributes());
              coreThreads.record(threadPoolExecutor.getCorePoolSize(), metrics.attributes());
              maxThreads.record(threadPoolExecutor.getMaximumPoolSize(), metrics.attributes());
              queueSize.record(threadPoolExecutor.getQueue().size(), metrics.attributes());
              queueRemaining.record(
                  threadPoolExecutor.getQueue().remainingCapacity(), metrics.attributes());
              completedTasks.record(
                  threadPoolExecutor.getCompletedTaskCount(), metrics.attributes());
            },
            threadCount,
            coreThreads,
            maxThreads,
            queueSize,
            queueRemaining,
            completedTasks));
    return callbackRef;
  }

  private static AtomicReference<BatchCallback> createBatchCallback(
      JvmExecutorMetrics metrics, Set<Thread> threads) {
    ObservableLongMeasurement threadCount = metrics.threadCount();
    WeakReference<Set<Thread>> threadsRef = new WeakReference<>(threads);
    AtomicReference<BatchCallback> callbackRef = new AtomicReference<>();
    callbackRef.set(
        metrics.batchCallback(
            () -> {
              Set<Thread> activeThreads = threadsRef.get();
              if (activeThreads == null) {
                closeCallback(callbackRef);
                return;
              }

              threadCount.record(activeThreads.size(), metrics.activeThreadAttributes());
            },
            threadCount));
    return callbackRef;
  }

  private static void closeCallback(AtomicReference<BatchCallback> callbackRef) {
    BatchCallback callback = callbackRef.getAndSet(null);
    if (callback != null) {
      callback.close();
    }
  }

  private static String executorName(@Nullable String threadName, String threadNameNormalization) {
    if (threadName == null) {
      return UNKNOWN;
    }

    threadName = threadName.trim();
    if (threadName.isEmpty()) {
      return UNKNOWN;
    }

    return ("trailing".equals(threadNameNormalization)
            ? TRAILING_DIGITS_PATTERN
            : ALL_DIGITS_PATTERN)
        .matcher(threadName)
        .replaceAll("*");
  }

  private static final class Registration {
    private final WeakReference<Set<Thread>> threadsRef;
    private String ownerName = UNKNOWN;
    private String threadNameNormalization = THREAD_NAME_NORMALIZATION;
    @Nullable private AtomicReference<BatchCallback> callback;
    @Nullable private LongCounter rejectedTasks;
    private Attributes attributes = Attributes.empty();
    private String threadName = UNKNOWN;
    private String executorName = UNKNOWN;
    private volatile boolean awaitingWorkerThread = true;
    private boolean closed;

    private Registration(Set<Thread> threads) {
      threadsRef = new WeakReference<>(threads);
    }

    private synchronized void awaitNextWorkerThread() {
      if (!closed) {
        awaitingWorkerThread = true;
      }
    }

    private void onWorkerThreadStarted(Executor executor, Thread thread) {
      if (!awaitingWorkerThread) {
        return;
      }

      @Nullable AtomicReference<BatchCallback> previous;
      synchronized (this) {
        if (closed || !awaitingWorkerThread) {
          return;
        }

        Set<Thread> threads = threadsRef.get();
        if (threads == null) {
          return;
        }

        awaitingWorkerThread = false;
        previous = callback;
        String newThreadName = thread.getName();
        registerMetrics(
            executor, threads, newThreadName, executorName(newThreadName, threadNameNormalization));
      }

      if (previous != null) {
        closeCallback(previous);
      }
    }

    private void reregister(
        Executor executor, String newOwnerName, String newThreadNameNormalization) {
      @Nullable AtomicReference<BatchCallback> previous;
      synchronized (this) {
        if (closed) {
          return;
        }

        String newExecutorName = executorName(threadName, newThreadNameNormalization);
        boolean metricsUnchanged =
            ownerName.equals(newOwnerName) && executorName.equals(newExecutorName);
        ownerName = newOwnerName;
        threadNameNormalization = newThreadNameNormalization;
        if (callback == null || metricsUnchanged) {
          return;
        }

        Set<Thread> threads = threadsRef.get();
        if (threads == null) {
          return;
        }

        previous = callback;
        registerMetrics(executor, threads, threadName, newExecutorName);
      }

      closeCallback(previous);
    }

    private synchronized void recordRejectedTask() {
      if (callback != null && rejectedTasks != null) {
        rejectedTasks.add(1, attributes);
      }
    }

    private void close() {
      @Nullable AtomicReference<BatchCallback> previous;
      synchronized (this) {
        closed = true;
        awaitingWorkerThread = false;
        previous = callback;
        callback = null;
        rejectedTasks = null;
        attributes = Attributes.empty();
      }

      if (previous != null) {
        closeCallback(previous);
      }
    }

    private void registerMetrics(
        Executor executor, Set<Thread> threads, String threadName, String executorName) {
      JvmExecutorMetrics metrics =
          JvmExecutorMetrics.create(
              GlobalOpenTelemetry.get(),
              INSTRUMENTATION_NAME,
              executorName,
              ownerName,
              executor.getClass().getName());

      if (executor instanceof ThreadPoolExecutor) {
        callback = createBatchCallback(metrics, (ThreadPoolExecutor) executor);
        rejectedTasks = metrics.rejectedTasks();
      } else {
        callback = createBatchCallback(metrics, threads);
        rejectedTasks = null;
      }

      attributes = metrics.attributes();
      this.threadName = threadName;
      this.executorName = executorName;
    }
  }

  private ThreadPoolExecutorMetrics() {}
}
