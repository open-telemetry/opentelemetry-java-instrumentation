/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class QueuedThreadPoolTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void dispatchPropagates() throws Exception {
    QueuedThreadPool pool = new QueuedThreadPool();
    // run test only if QueuedThreadPool has dispatch method
    // dispatch method was removed in jetty 9.1
    Method dispatch = null;
    try {
      dispatch = QueuedThreadPool.class.getMethod("dispatch", Runnable.class);
    } catch (NoSuchMethodException ignore) {
    }
    assumeTrue(dispatch != null);
    pool.start();

    Method finalDispatch = dispatch;
    testing.runWithSpan(
        "parent",
        () -> {
          // this child will have a span
          JavaAsyncChild child1 = new JavaAsyncChild();
          // this child won't
          JavaAsyncChild child2 = new JavaAsyncChild(false, false);
          if (finalDispatch != null) {
            finalDispatch.invoke(pool, child1);
            finalDispatch.invoke(pool, child2);
            child1.waitForCompletion();
            child2.waitForCompletion();
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("asyncChild")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));

    pool.stop();
  }

  @Test
  void dispatchPropagatesLambda() throws Exception {
    QueuedThreadPool pool = new QueuedThreadPool();
    // run test only if QueuedThreadPool has dispatch method
    // dispatch method was removed in jetty 9.1
    Method dispatch = null;
    try {
      dispatch = QueuedThreadPool.class.getMethod("dispatch", Runnable.class);
    } catch (NoSuchMethodException ignore) {
    }
    assumeTrue(dispatch != null);
    pool.start();

    JavaAsyncChild child = new JavaAsyncChild(true, true);
    Method finalDispatch = dispatch;
    testing.runWithSpan(
        "parent",
        () -> {
          if (finalDispatch != null) {
            finalDispatch.invoke(pool, JavaLambdaMaker.lambda(child));
          }
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

    pool.stop();
  }
}
