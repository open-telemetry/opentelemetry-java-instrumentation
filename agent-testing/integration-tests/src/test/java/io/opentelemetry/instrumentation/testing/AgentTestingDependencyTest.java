/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AgentTestingDependencyTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RepeatedTest(3)
  void accessesAgentTelemetryAndClearsBetweenTests() {
    assertThat(
            AgentClassLoaderAccess.loadClass(
                    "io.opentelemetry.javaagent.testing.exporter.AgentTestingExporterFactory")
                .getClassLoader())
        .isNotSameAs(getClass().getClassLoader());
    assertThat(Context.current()).isEqualTo(Context.root());
    assertThat(testing.spans()).isEmpty();

    testing.runWithSpan("agent-consumer", () -> {});
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("agent-consumer")));
  }

  @Test
  void detectsLeaksInAgentContextStorage() throws Exception {
    CountDownLatch attached = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> worker =
        executor.submit(
            () -> {
              try (Scope ignored =
                  Context.root().with(ContextKey.named("leaked"), "value").makeCurrent()) {
                attached.countDown();
                release.await();
              }
              return null;
            });
    try {
      assertThat(attached.await(10, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> AgentTestRunner.instance().closeContextStorage())
          .isInstanceOf(ConditionTimeoutException.class)
          .hasCauseInstanceOf(AssertionError.class);
    } finally {
      release.countDown();
      try {
        worker.get(10, TimeUnit.SECONDS);
      } finally {
        executor.shutdownNow();
      }
    }
    AgentTestRunner.instance().closeContextStorage();
  }
}
