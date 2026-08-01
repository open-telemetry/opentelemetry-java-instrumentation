/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors.metrics;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public abstract class ExecutorMetrics {

  public static final String UNKNOWN = "unknown";

  private static final Pattern TRAILING_DIGITS_PATTERN = Pattern.compile("\\d+$");
  private static final Pattern ALL_DIGITS_PATTERN = Pattern.compile("\\d+");

  private static final Cache<Executor, Registration> registrations = Cache.weak();

  public final void preRegister(ThreadPoolExecutor executor, String threadNameNormalization) {
    preRegister(executor, emptySet(), threadNameNormalization);
  }

  public final void preRegister(
      Executor executor, Set<Thread> threads, String threadNameNormalization) {
    registrations.computeIfAbsent(
        executor, ignored -> new Registration(threads, threadNameNormalization));
  }

  public final ThreadFactory preRegister(
      Executor executor,
      ThreadFactory threadFactory,
      Set<Thread> threads,
      String threadNameNormalization) {
    preRegister(executor, threads, threadNameNormalization);
    return new RegistrationThreadFactory(threadFactory);
  }

  protected abstract BatchCallback registerMetrics(
      Executor executor, Set<Thread> threads, String executorName, LongAdder rejectedTaskCount);

  public static void onThreadFactoryChanged(Executor executor) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.awaitNextWorkerThread();
    }
  }

  public static void onWorkerThreadStarted(Executor executor, @Nullable String threadName) {
    Registration registration = registrations.get(executor);
    if (registration != null) {
      registration.onWorkerThreadStarted(executor, threadName);
    }
  }

  public static void onWorkerThreadStarted(
      Executor executor, ThreadFactory threadFactory, @Nullable String threadName) {
    if (threadFactory instanceof RegistrationThreadFactory) {
      ((RegistrationThreadFactory) threadFactory).onWorkerThreadStarted(executor, threadName);
    } else {
      onWorkerThreadStarted(executor, threadName);
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

    return ("trailing".equals(threadNameNormalization)
            ? TRAILING_DIGITS_PATTERN
            : ALL_DIGITS_PATTERN)
        .matcher(threadName)
        .replaceAll("*");
  }

  private final class Registration {
    private final WeakReference<Set<Thread>> threadsRef;
    private final String threadNameNormalization;
    private final LongAdder rejectedTaskCount = new LongAdder();
    @Nullable private BatchCallback callback;
    @Nullable private String executorName;
    private volatile boolean awaitingWorkerThread = true;
    private boolean closed;

    private Registration(Set<Thread> threads, String threadNameNormalization) {
      threadsRef = new WeakReference<>(threads);
      this.threadNameNormalization = threadNameNormalization;
    }

    private synchronized void awaitNextWorkerThread() {
      if (!closed) {
        awaitingWorkerThread = true;
      }
    }

    private void onWorkerThreadStarted(Executor executor, @Nullable String threadName) {
      if (!awaitingWorkerThread) {
        return;
      }

      @Nullable BatchCallback previous;
      synchronized (this) {
        if (closed || !awaitingWorkerThread) {
          return;
        }

        Set<Thread> threads = threadsRef.get();
        if (threads == null) {
          return;
        }

        String newExecutorName = executorName(threadName, threadNameNormalization);
        if (callback != null && newExecutorName.equals(executorName)) {
          awaitingWorkerThread = false;
          return;
        }

        BatchCallback newCallback =
            registerMetrics(executor, threads, newExecutorName, rejectedTaskCount);
        previous = callback;
        callback = newCallback;
        executorName = newExecutorName;
        awaitingWorkerThread = false;
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

    private void onWorkerThreadStarted(Executor executor, @Nullable String threadName) {
      if (!registrationPending) {
        return;
      }
      ExecutorMetrics.onWorkerThreadStarted(executor, threadName);
      registrationPending = false;
    }

    private void clearPendingRegistration() {
      registrationPending = false;
    }
  }

  protected ExecutorMetrics() {}
}
