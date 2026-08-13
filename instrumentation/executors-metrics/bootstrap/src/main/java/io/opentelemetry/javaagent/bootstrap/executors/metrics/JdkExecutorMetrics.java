/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;

public final class JdkExecutorMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors-metrics";

  public static final String DEFAULT_NAME_NORMALIZATION =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "executors_metrics")
          .getString("name_normalization/development", "trailing");

  public static void reregister(
      Executor executor, @Nullable String ownerName, String threadNameNormalization) {
    ExecutorMetricsRegistry.reregister(
        executor, ownerName, threadNameNormalization, JdkExecutorMetrics::registerMetrics);
  }

  public static void onWorkerThreadStarted(Executor executor, @Nullable String threadName) {
    ExecutorMetricsRegistry.onWorkerThreadStarted(
        executor, threadName, JdkExecutorMetrics::registerMetrics);
  }

  public static void onWorkerThreadStarted(
      Executor executor, ThreadFactory threadFactory, @Nullable String threadName) {
    ExecutorMetricsRegistry.onWorkerThreadStarted(
        executor, threadFactory, threadName, JdkExecutorMetrics::registerMetrics);
  }

  private static BatchCallback registerMetrics(
      Executor executor,
      Set<Thread> threads,
      String executorName,
      @Nullable String ownerName,
      long queueCapacity,
      LongAdder rejectedTaskCount) {
    JvmExecutorMetrics metrics =
        JvmExecutorMetrics.create(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            executorName,
            ownerName,
            executor.getClass().getName());

    if (executor instanceof ThreadPoolExecutor) {
      return createBatchCallback(
          metrics, (ThreadPoolExecutor) executor, queueCapacity, rejectedTaskCount);
    }
    return createBatchCallback(metrics, threads);
  }

  private static BatchCallback createBatchCallback(
      JvmExecutorMetrics metrics,
      ThreadPoolExecutor executor,
      long queueCapacityValue,
      LongAdder rejectedTaskCount) {
    ObservableLongMeasurement threadCount = metrics.threadCount();
    ObservableLongMeasurement coreThreads = metrics.coreThreads();
    ObservableLongMeasurement maxThreads = metrics.maxThreads();
    ObservableLongMeasurement queueSize = metrics.queueSize();
    ObservableLongMeasurement queueCapacity = metrics.queueCapacity();
    ObservableLongMeasurement completedTasks = metrics.completedTasks();
    ObservableLongMeasurement rejectedTasks = metrics.rejectedTasks();

    WeakReference<ThreadPoolExecutor> executorRef = new WeakReference<>(executor);
    AtomicReference<BatchCallback> callbackRef = new AtomicReference<>();
    BatchCallback callback =
        metrics.batchCallback(
            () -> {
              ThreadPoolExecutor threadPoolExecutor = executorRef.get();
              if (threadPoolExecutor == null) {
                closeCallback(callbackRef);
                return;
              }

              long active = threadPoolExecutor.getActiveCount();
              threadCount.record(active, metrics.getActiveThreadAttributes());
              threadCount.record(
                  Math.max(threadPoolExecutor.getPoolSize() - active, 0),
                  metrics.getIdleThreadAttributes());
              coreThreads.record(threadPoolExecutor.getCorePoolSize(), metrics.getAttributes());
              maxThreads.record(threadPoolExecutor.getMaximumPoolSize(), metrics.getAttributes());
              queueSize.record(threadPoolExecutor.getQueue().size(), metrics.getAttributes());
              if (queueCapacityValue < Integer.MAX_VALUE) {
                queueCapacity.record(queueCapacityValue, metrics.getAttributes());
              }
              completedTasks.record(
                  threadPoolExecutor.getCompletedTaskCount(), metrics.getAttributes());
              long rejected = rejectedTaskCount.sum();
              if (rejected > 0) {
                rejectedTasks.record(rejected, metrics.getAttributes());
              }
            },
            threadCount,
            coreThreads,
            maxThreads,
            queueSize,
            queueCapacity,
            completedTasks,
            rejectedTasks);
    callbackRef.set(callback);
    return callback;
  }

  private static BatchCallback createBatchCallback(
      JvmExecutorMetrics metrics, Set<Thread> threads) {
    ObservableLongMeasurement threadCount = metrics.threadCount();
    WeakReference<Set<Thread>> threadsRef = new WeakReference<>(threads);
    AtomicReference<BatchCallback> callbackRef = new AtomicReference<>();
    BatchCallback callback =
        metrics.batchCallback(
            () -> {
              Set<Thread> activeThreads = threadsRef.get();
              if (activeThreads == null) {
                closeCallback(callbackRef);
                return;
              }

              threadCount.record(activeThreads.size(), metrics.getActiveThreadAttributes());
            },
            threadCount);
    callbackRef.set(callback);
    return callback;
  }

  private static void closeCallback(AtomicReference<BatchCallback> callbackRef) {
    BatchCallback callback = callbackRef.getAndSet(null);
    if (callback != null) {
      callback.close();
    }
  }

  private JdkExecutorMetrics() {}
}
