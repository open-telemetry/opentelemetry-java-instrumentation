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
import java.util.concurrent.ThreadFactory;
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

  private static final Cache<Executor, MetricsRegistration> registrations = Cache.weak();

  public static void register(ThreadPoolExecutor executor, ThreadFactory threadFactory) {
    registerMetrics(executor, threadFactory, emptySet());
  }

  public static void register(Executor executor, ThreadFactory threadFactory, Set<Thread> threads) {
    registerMetrics(executor, threadFactory, threads);
  }

  private static void registerMetrics(
      Executor executor, ThreadFactory threadFactory, Set<Thread> threads) {
    registrations.computeIfAbsent(
        executor,
        ignored -> {
          String threadName = threadName(threadFactory);
          return createRegistration(
              executor,
              threads,
              UNKNOWN,
              threadName,
              executorName(threadName, THREAD_NAME_NORMALIZATION));
        });
  }

  public static void reregister(
      Executor executor, @Nullable String ownerName, String threadNameNormalization) {
    String executorOwnerName = ownerName == null ? UNKNOWN : ownerName;
    MetricsRegistration registration = registrations.get(executor);
    if (registration != null) {
      updateRegistration(
          executor,
          registration,
          registration.reregister(
              executor,
              executorOwnerName,
              executorName(registration.threadName, threadNameNormalization)));
    }
  }

  public static void reregister(ThreadPoolExecutor executor, ThreadFactory threadFactory) {
    MetricsRegistration registration = registrations.get(executor);
    if (registration != null) {
      updateRegistration(executor, registration, registration.reregister(executor, threadFactory));
    }
  }

  public static void recordRejectedTask(Executor executor) {
    MetricsRegistration registration = registrations.get(executor);
    if (registration != null && registration.rejectedTasks != null) {
      registration.rejectedTasks.add(1, registration.attributes);
    }
  }

  public static void unregister(Executor executor) {
    MetricsRegistration registration;
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

  private static void updateRegistration(
      Executor executor,
      MetricsRegistration registration,
      @Nullable MetricsRegistration replacement) {
    boolean closeRegistration = false;
    @Nullable MetricsRegistration discardedReplacement = null;
    synchronized (registrations) {
      if (registrations.get(executor) != registration) {
        if (replacement != registration) {
          discardedReplacement = replacement;
        }
      } else if (replacement == null) {
        registrations.remove(executor);
        closeRegistration = true;
      } else if (replacement != registration) {
        registrations.put(executor, replacement);
        closeRegistration = true;
      }
    }
    if (closeRegistration) {
      registration.close();
    }
    if (discardedReplacement != null) {
      discardedReplacement.close();
    }
  }

  private static MetricsRegistration createRegistration(
      Executor executor,
      Set<Thread> threads,
      String ownerName,
      String threadName,
      String executorName) {
    JvmExecutorMetrics metrics =
        JvmExecutorMetrics.create(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            executorName,
            ownerName,
            executor.getClass().getName());

    AtomicReference<BatchCallback> callback;
    @Nullable LongCounter rejectedTasks;
    if (executor instanceof ThreadPoolExecutor) {
      callback = createBatchCallback(metrics, (ThreadPoolExecutor) executor);
      rejectedTasks = metrics.rejectedTasks();
    } else {
      callback = createBatchCallback(metrics, threads);
      rejectedTasks = null;
    }

    WeakReference<Set<Thread>> threadsRef = new WeakReference<>(threads);

    return new MetricsRegistration(
        callback,
        rejectedTasks,
        metrics.attributes(),
        threadName,
        executorName,
        ownerName,
        (newExecutor, newOwnerName, newExecutorName) -> {
          Set<Thread> activeThreads = threadsRef.get();
          return activeThreads == null
              ? null
              : createRegistration(
                  newExecutor, activeThreads, newOwnerName, threadName, newExecutorName);
        });
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

  private static String threadName(@Nullable ThreadFactory threadFactory) {
    try {
      if (threadFactory != null) {
        Thread thread = threadFactory.newThread(ThreadPoolExecutorMetrics::noopRunnable);
        if (thread != null) {
          String threadName = thread.getName();
          if (threadName != null) {
            threadName = threadName.trim();
            if (!threadName.isEmpty()) {
              return threadName;
            }
          }
        }
      }
    } catch (Throwable ignored) {
      // Use the fallback name when the thread factory cannot be safely probed.
    }
    return UNKNOWN;
  }

  private static void noopRunnable() {}

  private interface RegistrationFactory {
    @Nullable
    MetricsRegistration create(Executor executor, String ownerName, String executorName);
  }

  private static final class MetricsRegistration {
    private final AtomicReference<BatchCallback> callback;
    @Nullable private final LongCounter rejectedTasks;
    private final Attributes attributes;
    private final String threadName;
    private final String executorName;
    private final String ownerName;
    private final RegistrationFactory registrationFactory;

    private MetricsRegistration(
        AtomicReference<BatchCallback> callback,
        @Nullable LongCounter rejectedTasks,
        Attributes attributes,
        String threadName,
        String executorName,
        String ownerName,
        RegistrationFactory registrationFactory) {
      this.callback = callback;
      this.rejectedTasks = rejectedTasks;
      this.attributes = attributes;
      this.threadName = threadName;
      this.executorName = executorName;
      this.ownerName = ownerName;
      this.registrationFactory = registrationFactory;
    }

    @Nullable
    private MetricsRegistration reregister(
        Executor executor, String ownerName, String newExecutorName) {
      if (this.ownerName.equals(ownerName) && executorName.equals(newExecutorName)) {
        return this;
      }

      MetricsRegistration replacement =
          registrationFactory.create(executor, ownerName, newExecutorName);
      return replacement;
    }

    private MetricsRegistration reregister(
        ThreadPoolExecutor executor, ThreadFactory threadFactory) {
      String newThreadName = ThreadPoolExecutorMetrics.threadName(threadFactory);
      String newExecutorName = executorName(newThreadName, THREAD_NAME_NORMALIZATION);
      if (threadName.equals(newThreadName) && executorName.equals(newExecutorName)) {
        return this;
      }

      MetricsRegistration replacement =
          createRegistration(executor, emptySet(), ownerName, newThreadName, newExecutorName);
      return replacement;
    }

    private void close() {
      closeCallback(callback);
    }
  }

  private ThreadPoolExecutorMetrics() {}
}
