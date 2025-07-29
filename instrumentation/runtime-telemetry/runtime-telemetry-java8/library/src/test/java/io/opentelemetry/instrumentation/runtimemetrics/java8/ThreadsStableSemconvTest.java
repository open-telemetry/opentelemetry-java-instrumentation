/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import static io.opentelemetry.instrumentation.runtimemetrics.java8.ScopeUtil.EXPECTED_SCOPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.JvmAttributes.JVM_THREAD_DAEMON;
import static io.opentelemetry.semconv.JvmAttributes.JVM_THREAD_STATE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class ThreadsStableSemconvTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Mock private ThreadMXBean threadBean;

  @Test
  @EnabledOnJre(JRE.JAVA_8)
  void registerObservers_Java8Jmx() {
    when(threadBean.getThreadCount()).thenReturn(7);
    when(threadBean.getDaemonThreadCount()).thenReturn(2);

    Threads.INSTANCE
        .registerObservers(testing.getOpenTelemetry(), threadBean)
        .forEach(cleanup::deferCleanup);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Number of executing platform threads.")
                        .hasUnit("{thread}")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(2)
                                                .hasAttributesSatisfying(
                                                    equalTo(JVM_THREAD_DAEMON, true)),
                                        point ->
                                            point
                                                .hasValue(5)
                                                .hasAttributesSatisfying(
                                                    equalTo(JVM_THREAD_DAEMON, false))))));
  }

  @Test
  void registerObservers_Java8Thread() {
    Thread threadInfo1 = mock(Thread.class, new ThreadInfoAnswer(false, Thread.State.RUNNABLE));
    Thread threadInfo2 = mock(Thread.class, new ThreadInfoAnswer(true, Thread.State.WAITING));

    Thread[] threads = new Thread[] {threadInfo1, threadInfo2};

    Threads.INSTANCE
        .registerObservers(testing.getOpenTelemetry(), () -> threads)
        .forEach(cleanup::deferCleanup);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Number of executing platform threads.")
                        .hasUnit("{thread}")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributesSatisfying(
                                                    equalTo(JVM_THREAD_DAEMON, false),
                                                    equalTo(JVM_THREAD_STATE, "runnable")),
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributesSatisfying(
                                                    equalTo(JVM_THREAD_DAEMON, true),
                                                    equalTo(JVM_THREAD_STATE, "waiting"))))));
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_9)
  void registerObservers_Java9AndNewer() {
    ThreadInfo threadInfo1 =
        mock(ThreadInfo.class, new ThreadInfoAnswer(false, Thread.State.RUNNABLE));
    ThreadInfo threadInfo2 =
        mock(ThreadInfo.class, new ThreadInfoAnswer(true, Thread.State.WAITING));

    long[] threadIds = {12, 32, 42};
    when(threadBean.getAllThreadIds()).thenReturn(threadIds);
    when(threadBean.getThreadInfo(threadIds))
        .thenReturn(new ThreadInfo[] {threadInfo1, null, threadInfo2});

    Threads.INSTANCE
        .registerObservers(testing.getOpenTelemetry(), threadBean)
        .forEach(cleanup::deferCleanup);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.thread.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Number of executing platform threads.")
                        .hasUnit("{thread}")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributesSatisfying(
                                                    equalTo(JVM_THREAD_DAEMON, false),
                                                    equalTo(JVM_THREAD_STATE, "runnable")),
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributesSatisfying(
                                                    equalTo(JVM_THREAD_DAEMON, true),
                                                    equalTo(JVM_THREAD_STATE, "waiting"))))));
  }

  @Test
  void getThreads() {
    Thread[] threads = Threads.getThreads();
    Set<Thread> set = new HashSet<>(Arrays.asList(threads));
    assertThat(set).contains(Thread.currentThread());
  }

  static final class ThreadInfoAnswer implements Answer<Object> {

    private final boolean isDaemon;
    private final Thread.State state;

    ThreadInfoAnswer(boolean isDaemon, Thread.State state) {
      this.isDaemon = isDaemon;
      this.state = state;
    }

    @Override
    public Object answer(InvocationOnMock invocation) {
      String methodName = invocation.getMethod().getName();
      if (methodName.equals("isDaemon")) {
        return isDaemon;
      } else if (methodName.equals("getThreadState") || methodName.equals("getState")) {
        return state;
      }
      return null;
    }
  }
}
