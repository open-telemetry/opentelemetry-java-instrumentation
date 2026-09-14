/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors.metrics;

import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Field;
import java.time.Duration;
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

class ExecutorMetricsRegistryTest {

  private static final String NO_OWNER = "<none>";
  private static final long TEST_QUEUE_CAPACITY = 10;

  @Test
  void registrationThreadFactoryDelegatesNewThread() {
    ThreadPoolExecutor executor = newExecutor();
    AtomicReference<Runnable> delegatedTask = new AtomicReference<>();
    Runnable task = () -> {};
    Thread expectedThread = new Thread(task);
    ThreadFactory delegate =
        runnable -> {
          delegatedTask.set(runnable);
          return expectedThread;
        };
    ThreadFactory threadFactory =
        ExecutorMetricsRegistry.preRegister(executor, delegate, emptySet(), "all");
    try {
      assertThat(threadFactory.newThread(task)).isSameAs(expectedThread);
      assertThat(delegatedTask).hasValue(task);
    } finally {
      ExecutorMetricsRegistry.unregister(executor, threadFactory);
      executor.shutdownNow();
    }
  }

  @Test
  void fallsBackToRegistrationForUnwrappedThreadFactory() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");

      ExecutorMetricsRegistry.onWorkerThreadStarted(
          executor, Thread::new, "pool-1-thread-1", metrics);

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*");
      assertThat(metrics.queueCapacities).containsExactly(TEST_QUEUE_CAPACITY);
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void registrationThreadFactoryRegistersOnlyOnce() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    ThreadFactory threadFactory =
        ExecutorMetricsRegistry.preRegister(executor, Thread::new, emptySet(), "all");
    try {
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, threadFactory, "initial", metrics);

      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, threadFactory, "later", metrics);

      assertThat(metrics.executorNames).containsExactly("initial");
      assertThat(metrics.queueCapacities).containsExactly((long) Integer.MAX_VALUE);
    } finally {
      ExecutorMetricsRegistry.unregister(executor, threadFactory);
      executor.shutdownNow();
    }
  }

  @Test
  void registrationThreadFactoryPreRegisterDoesNotResetExistingRegistration() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
    ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "initial", metrics);

    ThreadFactory threadFactory =
        ExecutorMetricsRegistry.preRegister(executor, Thread::new, emptySet(), "all");
    try {
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, threadFactory, "later", metrics);

      assertThat(metrics.executorNames).containsExactly("initial");
    } finally {
      ExecutorMetricsRegistry.unregister(executor, threadFactory);
      executor.shutdownNow();
    }
  }

  @Test
  void unregisterDisablesRegistrationThreadFactory() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    ThreadFactory oldFactory =
        ExecutorMetricsRegistry.preRegister(executor, Thread::new, emptySet(), "all");

    ExecutorMetricsRegistry.unregister(executor, oldFactory);

    ThreadFactory newFactory =
        ExecutorMetricsRegistry.preRegister(executor, Thread::new, emptySet(), "all");
    try {
      ExecutorMetricsRegistry.onWorkerThreadStarted(
          executor, oldFactory, "ignored-1-thread-1", metrics);
      ExecutorMetricsRegistry.onWorkerThreadStarted(
          executor, newFactory, "pool-2-thread-2", metrics);

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*");
    } finally {
      ExecutorMetricsRegistry.unregister(executor, newFactory);
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @MethodSource("executorNames")
  void normalizesExecutorName(
      String threadNameNormalization, String threadName, String expectedExecutorName) {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, threadNameNormalization);

      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, threadName, metrics);

      assertThat(metrics.executorNames).containsExactly(expectedExecutorName);
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  private static Stream<Arguments> executorNames() {
    return Stream.of(
        Arguments.of("all", "pool-12-thread-34", "pool-*-thread-*"),
        Arguments.of("trailing", "pool-12-thread-34", "pool-12-thread-*"),
        Arguments.of("unsupported", "pool-12-thread-34", "pool-12-thread-*"),
        Arguments.of("", "pool-12-thread-34", "pool-12-thread-*"),
        Arguments.of("all", null, ExecutorMetricsRegistry.UNKNOWN),
        Arguments.of("all", "   ", ExecutorMetricsRegistry.UNKNOWN));
  }

  @Test
  void reregisterBeforeFirstWorkerUpdatesPendingIdentity() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");

      ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics);
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "pool-12-thread-34", metrics);

      assertThat(metrics.executorNames).containsExactly("pool-12-thread-*");
      assertThat(metrics.ownerNames).containsExactly("tomcat");
      assertThat(metrics.callbacks).hasSize(1);
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void workerUsesLatestNormalizationWhenReregisteredConcurrently() throws Exception {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
    Thread worker =
        new Thread(
            () ->
                ExecutorMetricsRegistry.onWorkerThreadStarted(
                    executor, "pool-12-thread-34", metrics));

    try {
      Object registration = registrationFor(executor);
      synchronized (registration) {
        worker.start();
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> worker.getState() == Thread.State.BLOCKED);

        ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics);
      }

      worker.join(10_000);

      assertThat(worker.isAlive()).isFalse();
      assertThat(metrics.executorNames).containsExactly("pool-12-thread-*");
      assertThat(metrics.ownerNames).containsExactly("tomcat");
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
      worker.join(10_000);
    }
  }

  @Test
  void reregistersActiveMetricsAndRemovesOwner() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "pool-12-thread-34", metrics);
      TestCallback originalCallback = metrics.callbacks.get(0);
      LongAdder rejectedTaskCount = metrics.rejectedTaskCounts.get(0);

      ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics);

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*", "pool-12-thread-*");
      assertThat(metrics.ownerNames).containsExactly(NO_OWNER, "tomcat");
      assertThat(metrics.callbacks).hasSize(2);
      assertThat(originalCallback.closeCount).hasValue(1);
      assertThat(metrics.rejectedTaskCounts)
          .allSatisfy(count -> assertThat(count).isSameAs(rejectedTaskCount));
      assertThat(metrics.queueCapacities).containsExactly(TEST_QUEUE_CAPACITY, TEST_QUEUE_CAPACITY);

      ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics);

      assertThat(metrics.executorNames).hasSize(2);
      assertThat(metrics.callbacks).hasSize(2);

      TestCallback ownerCallback = metrics.callbacks.get(1);
      ExecutorMetricsRegistry.reregister(executor, null, "trailing", metrics);

      assertThat(metrics.executorNames)
          .containsExactly("pool-*-thread-*", "pool-12-thread-*", "pool-12-thread-*");
      assertThat(metrics.ownerNames).containsExactly(NO_OWNER, "tomcat", NO_OWNER);
      assertThat(metrics.callbacks).hasSize(3);
      assertThat(ownerCallback.closeCount).hasValue(1);
      assertThat(metrics.callbacks.get(2).closeCount).hasValue(0);
      assertThat(metrics.rejectedTaskCounts)
          .allSatisfy(count -> assertThat(count).isSameAs(rejectedTaskCount));
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void keepsExistingCallbackAndRetriesReregistrationAfterFailure() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "pool-12-thread-34", metrics);
      TestCallback originalCallback = metrics.callbacks.get(0);

      metrics.failuresRemaining = 1;
      assertThatThrownBy(
              () -> ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("registration failed");

      assertThat(metrics.callbacks).containsExactly(originalCallback);
      assertThat(originalCallback.closeCount).hasValue(0);

      ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics);

      assertThat(metrics.executorNames)
          .containsExactly("pool-*-thread-*", "pool-12-thread-*", "pool-12-thread-*");
      assertThat(metrics.ownerNames).containsExactly(NO_OWNER, "tomcat", "tomcat");
      assertThat(metrics.callbacks).hasSize(2);
      assertThat(originalCallback.closeCount).hasValue(1);
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void doesNotReregisterAfterUnregister() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "pool-12-thread-34", metrics);
      TestCallback callback = metrics.callbacks.get(0);

      ExecutorMetricsRegistry.unregister(executor);
      ExecutorMetricsRegistry.reregister(executor, "tomcat", "trailing", metrics);

      assertThat(metrics.executorNames).containsExactly("pool-*-thread-*");
      assertThat(metrics.ownerNames).containsExactly(NO_OWNER);
      assertThat(metrics.callbacks).containsExactly(callback);
      assertThat(callback.closeCount).hasValue(1);
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void includesRejectionsRecordedBeforeFirstWorker() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
      ExecutorMetricsRegistry.recordRejectedTask(executor);

      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "pool-1-thread-1", metrics);

      assertThat(metrics.rejectedTaskCounts)
          .singleElement()
          .satisfies(count -> assertThat(count.sum()).isEqualTo(1));
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void retriesInitialRegistrationAfterFailure() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
      metrics.failuresRemaining = 1;
      assertThatThrownBy(
              () ->
                  ExecutorMetricsRegistry.onWorkerThreadStarted(
                      executor, "first-1-thread-1", metrics))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("registration failed");
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "second-2-thread-2", metrics);
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "third-3-thread-3", metrics);

      assertThat(metrics.executorNames).containsExactly("first-*-thread-*", "second-*-thread-*");
      assertThat(metrics.callbacks).hasSize(1);
      assertThat(metrics.callbacks.get(0).closeCount).hasValue(0);
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  @Test
  void repeatedUnregisterClosesCallbackOnce() {
    TestMetricsRegistrar metrics = new TestMetricsRegistrar();
    ThreadPoolExecutor executor = newExecutor();
    try {
      ExecutorMetricsRegistry.preRegister(executor, TEST_QUEUE_CAPACITY, "all");
      ExecutorMetricsRegistry.onWorkerThreadStarted(executor, "pool-1-thread-1", metrics);

      ExecutorMetricsRegistry.unregister(executor);
      ExecutorMetricsRegistry.unregister(executor);

      assertThat(metrics.callbacks)
          .singleElement()
          .satisfies(callback -> assertThat(callback.closeCount).hasValue(1));
    } finally {
      ExecutorMetricsRegistry.unregister(executor);
      executor.shutdownNow();
    }
  }

  private static ThreadPoolExecutor newExecutor() {
    return new ThreadPoolExecutor(0, 1, 1, MINUTES, new LinkedBlockingQueue<>());
  }

  @SuppressWarnings("unchecked")
  private static Object registrationFor(Executor executor) throws Exception {
    Field registrationsField = ExecutorMetricsRegistry.class.getDeclaredField("registrations");
    registrationsField.setAccessible(true);
    Cache<Executor, ?> registrations = (Cache<Executor, ?>) registrationsField.get(null);
    return registrations.get(executor);
  }

  private static final class TestMetricsRegistrar
      implements ExecutorMetricsRegistry.MetricsRegistrar {
    private final List<String> executorNames = new ArrayList<>();
    private final List<String> ownerNames = new ArrayList<>();
    private final List<Long> queueCapacities = new ArrayList<>();
    private final List<LongAdder> rejectedTaskCounts = new ArrayList<>();
    private final List<TestCallback> callbacks = new ArrayList<>();
    private int failuresRemaining;

    @Override
    public BatchCallback registerMetrics(
        Executor executor,
        Set<Thread> threads,
        String executorName,
        String ownerName,
        long queueCapacity,
        LongAdder rejectedTaskCount) {
      executorNames.add(executorName);
      ownerNames.add(ownerName == null ? NO_OWNER : ownerName);
      queueCapacities.add(queueCapacity);
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
