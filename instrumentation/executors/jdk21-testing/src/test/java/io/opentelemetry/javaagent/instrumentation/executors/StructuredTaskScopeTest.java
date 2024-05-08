/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("preview")
class StructuredTaskScopeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void multipleForkJoin() throws Exception {
    StructuredTaskScope<Object> taskScope = new StructuredTaskScope.ShutdownOnFailure();

    Callable<String> callable1 =
        () -> {
          testing.runWithSpan("task1", () -> {});
          return "a";
        };
    Callable<String> callable2 =
        () -> {
          testing.runWithSpan("task2", () -> {});
          return "b";
        };

    String result =
        testing.runWithSpan(
            "parent",
            () -> {
              StructuredTaskScope.Subtask<String> fork1 = taskScope.fork(callable1);
              StructuredTaskScope.Subtask<String> fork2 = taskScope.fork(callable2);
              taskScope.join();

              return "" + fork1.get() + fork2.get();
            });

    assertThat(result).isEqualTo("ab");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("task1").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("task2").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));

    taskScope.close();
  }
}
