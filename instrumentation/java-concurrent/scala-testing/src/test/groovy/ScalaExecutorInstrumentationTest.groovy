/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.data.SpanData
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.forkjoin.ForkJoinTask
import spock.lang.Shared

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

/**
 * Test executor instrumentation for Scala specific classes.
 * This is to large extent a copy of ExecutorInstrumentationTest.
 */
class ScalaExecutorInstrumentationTest extends AgentTestRunner {

  @Shared
  def executeRunnable = { e, c -> e.execute((Runnable) c) }
  @Shared
  def scalaExecuteForkJoinTask = { e, c -> e.execute((ForkJoinTask) c) }
  @Shared
  def submitRunnable = { e, c -> e.submit((Runnable) c) }
  @Shared
  def submitCallable = { e, c -> e.submit((Callable) c) }
  @Shared
  def scalaSubmitForkJoinTask = { e, c -> e.submit((ForkJoinTask) c) }
  @Shared
  def scalaInvokeForkJoinTask = { e, c -> e.invoke((ForkJoinTask) c) }

  def "#poolImpl '#name' propagates"() {
    setup:
    def pool = poolImpl
    def m = method

    new Runnable() {
      @Override
      void run() {
        runUnderTrace("parent") {
          // this child will have a span
          def child1 = new ScalaAsyncChild()
          // this child won't
          def child2 = new ScalaAsyncChild(false, false)
          m(pool, child1)
          m(pool, child2)
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
    trace.get(0).name == "parent"
    trace.get(1).name == "asyncChild"
    trace.get(1).parentSpanId == trace.get(0).spanId

    cleanup:
    pool?.shutdown()

    // Unfortunately, there's no simple way to test the cross product of methods/pools.
    where:
    name                   | method                   | poolImpl
    "execute Runnable"     | executeRunnable          | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "submit Runnable"      | submitRunnable           | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "submit Callable"      | submitCallable           | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))

    // ForkJoinPool has additional set of method overloads for ForkJoinTask to deal with
    "execute Runnable"     | executeRunnable          | new ForkJoinPool()
    "execute ForkJoinTask" | scalaExecuteForkJoinTask | new ForkJoinPool()
    "submit Runnable"      | submitRunnable           | new ForkJoinPool()
    "submit Callable"      | submitCallable           | new ForkJoinPool()
    "submit ForkJoinTask"  | scalaSubmitForkJoinTask  | new ForkJoinPool()
    "invoke ForkJoinTask"  | scalaInvokeForkJoinTask  | new ForkJoinPool()
  }

  def "#poolImpl '#name' reports after canceled jobs"() {
    setup:
    def pool = poolImpl
    def m = method
    List<ScalaAsyncChild> children = new ArrayList<>()
    List<Future> jobFutures = new ArrayList<>()

    new Runnable() {
      @Override
      void run() {
        runUnderTrace("parent") {
          try {
            for (int i = 0; i < 20; ++i) {
              // Our current instrumentation instrumentation does not behave very well
              // if we try to reuse Callable/Runnable. Namely we would be getting 'orphaned'
              // child traces sometimes since state can contain only one parent span - and
              // we do not really have a good way for attributing work to correct parent span
              // if we reuse Callable/Runnable.
              // Solution for now is to never reuse a Callable/Runnable.
              ScalaAsyncChild child = new ScalaAsyncChild(false, true)
              children.add(child)
              try {
                Future f = m(pool, child)
                jobFutures.add(f)
              } catch (InvocationTargetException e) {
                throw e.getCause()
              }
            }
          } catch (RejectedExecutionException e) {
          }

          for (Future f : jobFutures) {
            f.cancel(false)
          }
          for (ScalaAsyncChild child : children) {
            child.unblock()
          }
        }
      }
    }.run()

    TEST_WRITER.waitForTraces(1)

    expect:
    TEST_WRITER.traces.size() == 1

    where:
    name              | method         | poolImpl
    "submit Runnable" | submitRunnable | new ForkJoinPool()
    "submit Callable" | submitCallable | new ForkJoinPool()
  }
}
