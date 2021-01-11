/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.javaagent.instrumentation.jetty.JavaLambdaMaker
import io.opentelemetry.sdk.trace.data.SpanData
import org.eclipse.jetty.util.thread.QueuedThreadPool

class QueuedThreadPoolTest extends AgentTestRunner {

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
        runUnderTrace("parent") {
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

    TEST_WRITER.waitForTraces(1)
    List<SpanData> trace = TEST_WRITER.traces[0]

    expect:
    TEST_WRITER.traces.size() == 1
    trace.size() == 2
    trace.get(0).traceId == trace.get(1).traceId
    trace.get(0).name == "parent"
    trace.get(1).name == "asyncChild"
    trace.get(1).parentSpanId == trace.get(0).spanId

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
        runUnderTrace("parent") {
          pool.dispatch(JavaLambdaMaker.lambda(child))
        }
      }
    }.run()
    // We block in child to make sure spans close in predictable order
    child.unblock()
    child.waitForCompletion()

    TEST_WRITER.waitForTraces(1)
    List<SpanData> trace = TEST_WRITER.traces[0]

    expect:
    TEST_WRITER.traces.size() == 1
    trace.size() == 2
    trace.get(0).traceId == trace.get(1).traceId
    trace.get(0).name == "parent"
    trace.get(1).name == "asyncChild"
    trace.get(1).parentSpanId == trace.get(0).spanId

    cleanup:
    pool.stop()
  }
}
