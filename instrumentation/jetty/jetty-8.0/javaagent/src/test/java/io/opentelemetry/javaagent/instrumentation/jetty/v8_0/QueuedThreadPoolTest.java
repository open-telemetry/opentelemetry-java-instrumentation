/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class QueuedThreadPoolTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private QueuedThreadPool pool;
  private Method dispatchMethod;

  @BeforeEach
  void setupSpec() throws Exception {
    pool = new QueuedThreadPool();
    // run test only if QueuedThreadPool has dispatch method
    // dispatch method was removed in jetty 9.1
    dispatchMethod = QueuedThreadPool.class.getMethod("dispatch", new Class<?>[] {Runnable.class});
    pool.start();
  }

  @AfterEach
  void cleanupSpec() throws Exception {
    pool.stop();
  }

  @Test
  void dispatchPropagates() throws Exception {
    if (dispatchMethod == null) {
      return;
    }
    testing.runWithSpan(
        "parent",
        () -> {
          // this child will have a span
          JavaAsyncChild child1 = new JavaAsyncChild();
          // this child won't
          JavaAsyncChild child2 = new JavaAsyncChild(false, false);
          pool.dispatch(child1);
          pool.dispatch(child2);
          child1.waitForCompletion();
          child2.waitForCompletion();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("asyncChild")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void dispatchPropagatesLambda() throws InterruptedException {
    if (dispatchMethod == null) {
      return;
    }
    JavaAsyncChild child = new JavaAsyncChild(true, true);
    testing.runWithSpan(
        "parent",
        () -> {
          pool.dispatch(JavaLambdaMaker.lambda(child));
        });

    // We block in child to make sure spans close in predictable order
    child.unblock();
    child.waitForCompletion();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("asyncChild")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
