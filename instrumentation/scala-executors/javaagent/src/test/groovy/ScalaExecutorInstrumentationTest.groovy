/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.forkjoin.ForkJoinTask
import spock.lang.Shared

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Test executor instrumentation for Scala specific classes.
 * This is to large extent a copy of ExecutorInstrumentationTest.
 */
class ScalaExecutorInstrumentationTest extends AgentInstrumentationSpecification {

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

  def "#poolImpl '#testName' propagates"() {
    setup:
    def pool = poolImpl
    def m = method

    new Runnable() {
      @Override
      void run() {
        runWithSpan("parent") {
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
    pool?.shutdown()

    // Unfortunately, there's no simple way to test the cross product of methods/pools.
    where:
    testName               | method                   | poolImpl
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

  def "#poolImpl '#testName' reports after canceled jobs"() {
    setup:
    ExecutorService pool = poolImpl
    def m = method
    List<ScalaAsyncChild> children = new ArrayList<>()
    List<Future> jobFutures = new ArrayList<>()

    new Runnable() {
      @Override
      void run() {
        runWithSpan("parent") {
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

    expect:
    waitForTraces(1).size() == 1

    // Wait for shutdown to make sure any remaining tasks finish and cleanup context since we don't
    // wait on the tasks.
    pool.shutdown()
    pool.awaitTermination(10, TimeUnit.SECONDS)

    where:
    testName          | method         | poolImpl
    "submit Runnable" | submitRunnable | new ForkJoinPool()
    "submit Callable" | submitCallable | new ForkJoinPool()
  }
}
