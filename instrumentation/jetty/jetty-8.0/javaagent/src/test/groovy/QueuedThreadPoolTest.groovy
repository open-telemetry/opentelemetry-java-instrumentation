/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.jetty.JavaLambdaMaker
import org.eclipse.jetty.util.thread.QueuedThreadPool

import static org.junit.Assume.assumeTrue

class QueuedThreadPoolTest extends AgentInstrumentationSpecification {

  def "QueueThreadPool 'dispatch' propagates"() {
    setup:
    def pool = new QueuedThreadPool()
    // run test only if QueuedThreadPool has dispatch method
    // dispatch method was removed in jetty 9.1
    assumeTrue(pool.metaClass.getMetaMethod("dispatch", Runnable) != null)
    pool.start()

    new Runnable() {
      @Override
      void run() {
        runWithSpan("parent") {
          // this child will have a span
          def child1 = new JavaAsyncChild()
          // this child won't
          def child2 = new JavaAsyncChild(false, false)
          pool.dispatch(child1)
          pool.dispatch(child2)
          child1.waitForCompletion()
          child2.waitForCompletion()
        }
      }
    }.run()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "asyncChild"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    cleanup:
    pool.stop()
  }

  def "QueueThreadPool 'dispatch' propagates lambda"() {
    setup:
    def pool = new QueuedThreadPool()
    // run test only if QueuedThreadPool has dispatch method
    // dispatch method was removed in jetty 9.1
    assumeTrue(pool.metaClass.getMetaMethod("dispatch", Runnable) != null)
    pool.start()

    JavaAsyncChild child = new JavaAsyncChild(true, true)
    new Runnable() {
      @Override
      void run() {
        runWithSpan("parent") {
          pool.dispatch(JavaLambdaMaker.lambda(child))
        }
      }
    }.run()
    // We block in child to make sure spans close in predictable order
    child.unblock()
    child.waitForCompletion()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "asyncChild"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    cleanup:
    pool.stop()
  }
}
