/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors.metrics;

import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.metrics.BatchCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExecutorMetricsTest {

  @Test
  void registrationThreadFactoryDelegatesNewThread() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    AtomicReference<Runnable> delegatedTask = new AtomicReference<>();
    Runnable task = () -> {};
    Thread expectedThread = new Thread(task);
    ThreadFactory delegate =
        runnable -> {
          delegatedTask.set(runnable);
          return expectedThread;
        };
    ThreadFactory threadFactory = metrics.preRegister(executor, delegate, emptySet(), "all");
    try {
      assertThat(threadFactory.newThread(task)).isSameAs(expectedThread);
      assertThat(delegatedTask).hasValue(task);
    } finally {
      ExecutorMetrics.unregister(executor, threadFactory);
      executor.shutdownNow();
    }
  }

  @Test
  void fallsBackToRegistrationForUnwrappedThreadFactory() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    try {
      metrics.preRegister(executor, "all");

      ExecutorMetrics.onWorkerThreadStarted(executor, Thread::new, "pool-1-thread-1");

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*");
    } finally {
      ExecutorMetrics.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void registrationThreadFactoryRegistersOnlyOnce() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    ThreadFactory threadFactory = metrics.preRegister(executor, Thread::new, emptySet(), "all");
    try {
      ExecutorMetrics.onWorkerThreadStarted(executor, threadFactory, "initial");

      ExecutorMetrics.onThreadFactoryChanged(executor);
      ExecutorMetrics.onWorkerThreadStarted(executor, threadFactory, "later");

      assertThat(metrics.executorNames).containsExactly("initial");
    } finally {
      ExecutorMetrics.unregister(executor, threadFactory);
      executor.shutdownNow();
    }
  }

  @Test
  void registrationThreadFactoryPreRegisterDoesNotResetExistingRegistration() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    metrics.preRegister(executor, "all");
    ExecutorMetrics.onWorkerThreadStarted(executor, "initial");

    ThreadFactory threadFactory = metrics.preRegister(executor, Thread::new, emptySet(), "all");
    try {
      ExecutorMetrics.onWorkerThreadStarted(executor, threadFactory, "later");

      assertThat(metrics.executorNames).containsExactly("initial");
    } finally {
      ExecutorMetrics.unregister(executor, threadFactory);
      executor.shutdownNow();
    }
  }

  @Test
  void unregisterDisablesRegistrationThreadFactory() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    ThreadFactory oldFactory = metrics.preRegister(executor, Thread::new, emptySet(), "all");

    ExecutorMetrics.unregister(executor, oldFactory);

    ThreadFactory newFactory = metrics.preRegister(executor, Thread::new, emptySet(), "all");
    try {
      ExecutorMetrics.onWorkerThreadStarted(executor, oldFactory, "ignored-1-thread-1");
      ExecutorMetrics.onWorkerThreadStarted(executor, newFactory, "pool-2-thread-2");

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*");
    } finally {
      ExecutorMetrics.unregister(executor, newFactory);
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @MethodSource("executorNames")
  void normalizesExecutorName(
      String threadNameNormalization, String threadName, String expectedExecutorName) {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    try {
      metrics.preRegister(executor, threadNameNormalization);

      ExecutorMetrics.onWorkerThreadStarted(executor, threadName);

      assertThat(metrics.executorNames).containsExactly(expectedExecutorName);
    } finally {
      ExecutorMetrics.unregister(executor);
      executor.shutdownNow();
    }
  }

  private static Stream<Arguments> executorNames() {
    return Stream.of(
        Arguments.of("all", "pool-12-thread-34", "pool-*-thread-*"),
        Arguments.of("trailing", "pool-12-thread-34", "pool-12-thread-*"),
        Arguments.of("unsupported", "pool-12-thread-34", "pool-*-thread-*"),
        Arguments.of("all", null, ExecutorMetrics.UNKNOWN),
        Arguments.of("all", "   ", ExecutorMetrics.UNKNOWN));
  }

  @Test
  void includesRejectionsRecordedBeforeFirstWorker() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    try {
      metrics.preRegister(executor, "all");
      ExecutorMetrics.recordRejectedTask(executor);

      ExecutorMetrics.onWorkerThreadStarted(executor, "pool-1-thread-1");

      assertThat(metrics.rejectedTaskCounts)
          .singleElement()
          .satisfies(count -> assertThat(count.sum()).isEqualTo(1));
    } finally {
      ExecutorMetrics.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void keepsExistingCallbackAndRetriesRegistrationAfterFailure() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    try {
      metrics.preRegister(executor, "all");
      ExecutorMetrics.onWorkerThreadStarted(executor, "pool-1-thread-1");
      TestCallback originalCallback = metrics.callbacks.get(0);

      metrics.failuresRemaining = 1;
      ExecutorMetrics.onThreadFactoryChanged(executor);
      assertThatThrownBy(() -> ExecutorMetrics.onWorkerThreadStarted(executor, "other-2-thread-2"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("registration failed");
      assertThat(metrics.callbacks).containsExactly(originalCallback);
      assertThat(originalCallback.closeCount).hasValue(0);

      ExecutorMetrics.onWorkerThreadStarted(executor, "other-3-thread-3");

      assertThat(metrics.executorNames)
          .containsExactly("pool-*-thread-*", "other-*-thread-*", "other-*-thread-*");
      assertThat(metrics.callbacks).hasSize(2);
      assertThat(originalCallback.closeCount).hasValue(1);
      LongAdder rejectedTaskCount = metrics.rejectedTaskCounts.get(0);
      assertThat(metrics.rejectedTaskCounts)
          .allSatisfy(count -> assertThat(count).isSameAs(rejectedTaskCount));

      ExecutorMetrics.onThreadFactoryChanged(executor);
      ExecutorMetrics.onWorkerThreadStarted(executor, "other-4-thread-4");

      assertThat(metrics.executorNames).hasSize(3);
      assertThat(metrics.callbacks).hasSize(2);
      assertThat(metrics.callbacks.get(1).closeCount).hasValue(0);
    } finally {
      ExecutorMetrics.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void doesNotReplaceCallbackForSameNormalizedName() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    try {
      metrics.preRegister(executor, "all");
      ExecutorMetrics.onWorkerThreadStarted(executor, "pool-1-thread-1");

      ExecutorMetrics.onThreadFactoryChanged(executor);
      ExecutorMetrics.onWorkerThreadStarted(executor, "pool-2-thread-2");

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*");
      assertThat(metrics.callbacks)
          .singleElement()
          .satisfies(callback -> assertThat(callback.closeCount).hasValue(0));
    } finally {
      ExecutorMetrics.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void repeatedUnregisterClosesCallbackOnce() {
    TestExecutorMetrics metrics = new TestExecutorMetrics();
    ThreadPoolExecutor executor = newExecutor();
    try {
      metrics.preRegister(executor, "all");
      ExecutorMetrics.onWorkerThreadStarted(executor, "pool-1-thread-1");

      ExecutorMetrics.unregister(executor);
      ExecutorMetrics.unregister(executor);

      assertThat(metrics.callbacks)
          .singleElement()
          .satisfies(callback -> assertThat(callback.closeCount).hasValue(1));
    } finally {
      ExecutorMetrics.unregister(executor);
      executor.shutdownNow();
    }
  }

  private static ThreadPoolExecutor newExecutor() {
    return new ThreadPoolExecutor(0, 1, 1, MINUTES, new LinkedBlockingQueue<>());
  }

  private static final class TestExecutorMetrics extends ExecutorMetrics {
    private final List<String> executorNames = new ArrayList<>();
    private final List<LongAdder> rejectedTaskCounts = new ArrayList<>();
    private final List<TestCallback> callbacks = new ArrayList<>();
    private int failuresRemaining;

    @Override
    protected BatchCallback registerMetrics(
        Executor executor, Set<Thread> threads, String executorName, LongAdder rejectedTaskCount) {
      executorNames.add(executorName);
      rejectedTaskCounts.add(rejectedTaskCount);
      if (failuresRemaining > 0) {
        failuresRemaining--;
        throw new IllegalStateException("registration failed");
      }

      TestCallback callback = new TestCallback();
      callbacks.add(callback);
      return callback;
    }
  }

  private static final class TestCallback implements BatchCallback {
    private final AtomicInteger closeCount = new AtomicInteger();

    @Override
    public void close() {
      closeCount.incrementAndGet();
    }
  }
}
