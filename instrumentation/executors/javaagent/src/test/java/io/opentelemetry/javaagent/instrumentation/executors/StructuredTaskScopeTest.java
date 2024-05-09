/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;

@EnabledForJreRange(min = JRE.JAVA_21)
class StructuredTaskScopeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void multipleForkJoin() throws Exception {
    Class<?> sofTaskScopeClass =
        Class.forName("java.util.concurrent.StructuredTaskScope$ShutdownOnFailure");
    Object taskScope = sofTaskScopeClass.getDeclaredConstructor().newInstance();
    Class<?> taskScopeClass = Class.forName("java.util.concurrent.StructuredTaskScope");
    Method forkMethod = taskScopeClass.getDeclaredMethod("fork", Callable.class);
    Method joinMethod = taskScopeClass.getDeclaredMethod("join");
    Method closeMethod = taskScopeClass.getDeclaredMethod("close");

    Class<?> subtaskClass = Class.forName("java.util.concurrent.StructuredTaskScope$Subtask");
    Method getMethod = subtaskClass.getDeclaredMethod("get");

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
              try {
                Object fork1 = forkMethod.invoke(taskScope, callable1);
                Object fork2 = forkMethod.invoke(taskScope, callable2);
                joinMethod.invoke(taskScope);

                return "" + getMethod.invoke(fork1) + getMethod.invoke(fork2);
              } catch (Exception e) {
                throw new AssertionError(e);
              }
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

    closeMethod.invoke(taskScope);
  }
}
