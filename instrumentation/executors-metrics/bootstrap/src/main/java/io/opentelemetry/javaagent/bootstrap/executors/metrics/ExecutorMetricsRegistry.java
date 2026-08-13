/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors.metrics;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public final class ExecutorMetricsRegistry {

  public static final String UNKNOWN = "unknown";

  private static final long OMIT_QUEUE_CAPACITY = Integer.MAX_VALUE;
  private static final Pattern TRAILING_DIGITS_PATTERN = Pattern.compile("\\d+$");
  private static final Pattern ALL_DIGITS_PATTERN = Pattern.compile("\\d+");

  private static final Cache<Executor, Registration> registrations = Cache.weak();

  @FunctionalInterface
  public interface MetricsRegistrar {

    BatchCallback registerMetrics(
        Executor executor,
        Set<Thread> threads,
        String executorName,
        @Nullable String ownerName,
        long queueCapacity,
        LongAdder rejectedTaskCount);
  }

  public static void preRegister(
      ThreadPoolExecutor executor, long queueCapacity, String threadNameNormalization) {
    preRegister(executor, emptySet(), queueCapacity, threadNameNormalization);
  }

  public static void preRegister(
      Executor executor, Set<Thread> threads, String threadNameNormalization) {
    preRegister(executor, threads, OMIT_QUEUE_CAPACITY, threadNameNormalization);
  }

  private static void preRegister(
      Executor executor, Set<Thread> threads, long queueCapacity, String threadNameNormalization) {
    registrations.computeIfAbsent(
        executor, ignored -> new Registration(threads, queueCapacity, threadNameNormalization));
  }

  public static ThreadFactory preRegister(
      Executor executor,
      ThreadFactory threadFactory,
      Set<Thread> threads,
      String threadNameNormalization) {
    preRegister(executor, threads, threadNameNormalization);
    return new RegistrationThreadFactory(threadFactory);
  }

  public static void reregister(
      Executor executor,
      @Nullable String ownerName,
      String threadNameNormalization,
      MetricsRegistrar metricsRegistrar) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.reregister(executor, ownerName, threadNameNormalization, metricsRegistrar);
    }
  }

  public static void onWorkerThreadStarted(
      Executor executor, @Nullable String threadName, MetricsRegistrar metricsRegistrar) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.onWorkerThreadStarted(executor, threadName, metricsRegistrar);
    }
  }

  public static void onWorkerThreadStarted(
      Executor executor,
      ThreadFactory threadFactory,
      @Nullable String threadName,
      MetricsRegistrar metricsRegistrar) {
    if (threadFactory instanceof RegistrationThreadFactory) {
      ((RegistrationThreadFactory) threadFactory)
          .onWorkerThreadStarted(executor, threadName, metricsRegistrar);
    } else {
      onWorkerThreadStarted(executor, threadName, metricsRegistrar);
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

  public static void unregister(Executor executor, ThreadFactory threadFactory) {
    if (threadFactory instanceof RegistrationThreadFactory) {
      ((RegistrationThreadFactory) threadFactory).clearPendingRegistration();
    }
    unregister(executor);
  }

  private static String executorName(@Nullable String threadName, String threadNameNormalization) {
    if (threadName == null) {
      return UNKNOWN;
    }

    threadName = threadName.trim();
    if (threadName.isEmpty()) {
      return UNKNOWN;
    }

    return ("all".equals(threadNameNormalization) ? ALL_DIGITS_PATTERN : TRAILING_DIGITS_PATTERN)
        .matcher(threadName)
        .replaceAll("*");
  }

  private static final class Registration {
    private final WeakReference<Set<Thread>> threadsRef;
    private final long queueCapacity;
    private final LongAdder rejectedTaskCount = new LongAdder();
    @Nullable private String ownerName;
    private String threadNameNormalization;
    @Nullable private String threadName;
    @Nullable private String executorName;
    @Nullable private BatchCallback callback;
    private volatile boolean awaitingWorkerThread = true;
    private boolean closed;

    private Registration(Set<Thread> threads, long queueCapacity, String threadNameNormalization) {
      threadsRef = new WeakReference<>(threads);
      this.queueCapacity = queueCapacity;
      this.threadNameNormalization = threadNameNormalization;
    }

    private void onWorkerThreadStarted(
        Executor executor, @Nullable String threadName, MetricsRegistrar metricsRegistrar) {
      if (!awaitingWorkerThread) {
        return;
      }

      @Nullable BatchCallback previous;
      synchronized (this) {
        String newExecutorName = executorName(threadName, threadNameNormalization);
        if (closed || !awaitingWorkerThread || newExecutorName.equals(executorName)) {
          return;
        }

        Set<Thread> threads = threadsRef.get();
        if (threads == null) {
          return;
        }

        BatchCallback newCallback =
            metricsRegistrar.registerMetrics(
                executor, threads, newExecutorName, ownerName, queueCapacity, rejectedTaskCount);
        previous = callback;
        callback = newCallback;
        executorName = newExecutorName;
        this.threadName = threadName;
        awaitingWorkerThread = false;
      }

      if (previous != null) {
        previous.close();
      }
    }

    private void reregister(
        Executor executor,
        @Nullable String newOwnerName,
        String newThreadNameNormalization,
        MetricsRegistrar metricsRegistrar) {
      @Nullable BatchCallback previous;
      synchronized (this) {
        if (closed) {
          return;
        }

        String newExecutorName = executorName(threadName, newThreadNameNormalization);
        boolean metricsUnchanged =
            Objects.equals(ownerName, newOwnerName) && newExecutorName.equals(executorName);

        Set<Thread> threads = threadsRef.get();
        if (callback == null || threads == null || metricsUnchanged) {
          ownerName = newOwnerName;
          threadNameNormalization = newThreadNameNormalization;
          return;
        }

        BatchCallback newCallback =
            metricsRegistrar.registerMetrics(
                executor, threads, newExecutorName, newOwnerName, queueCapacity, rejectedTaskCount);
        previous = callback;
        callback = newCallback;
        ownerName = newOwnerName;
        threadNameNormalization = newThreadNameNormalization;
        executorName = newExecutorName;
      }

      if (previous != null) {
        previous.close();
      }
    }

    private void recordRejectedTask() {
      rejectedTaskCount.increment();
    }

    private void close() {
      @Nullable BatchCallback previous;
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
        awaitingWorkerThread = false;
        previous = callback;
        callback = null;
      }

      if (previous != null) {
        previous.close();
      }
    }
  }

  private static final class RegistrationThreadFactory implements ThreadFactory {
    private final ThreadFactory delegate;
    private volatile boolean registrationPending = true;

    private RegistrationThreadFactory(ThreadFactory delegate) {
      this.delegate = delegate;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      return delegate.newThread(runnable);
    }

    private void onWorkerThreadStarted(
        Executor executor, @Nullable String threadName, MetricsRegistrar metricsRegistrar) {
      if (!registrationPending) {
        return;
      }
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, threadName, metricsRegistrar);
      registrationPending = false;
    }

    private void clearPendingRegistration() {
      registrationPending = false;
    }
  }

  private ExecutorMetricsRegistry() {}
}
