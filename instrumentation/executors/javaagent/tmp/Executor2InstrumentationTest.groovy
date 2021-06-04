/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static TraceUtils.basicSpan
import static TraceUtils.runUnderTrace

import Span
import AgentInstrumentationSpecification
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import Shared
import Unroll

class Executor2InstrumentationTest extends AgentInstrumentationSpecification {

  @Shared
  def executeRunnable = { e, c -> e.execute((Runnable) c) }
  @Shared
  def executeForkJoinTask = { e, c -> e.execute((ForkJoinTask) c) }
  @Shared
  def submitRunnable = { e, c -> e.submit((Runnable) c) }
  @Shared
  def submitCallable = { e, c -> e.submit((Callable) c) }
  @Shared
  def submitForkJoinTask = { e, c -> e.submit((ForkJoinTask) c) }
  @Shared
  def invokeAll = { e, c -> e.invokeAll([(Callable) c]) }
  @Shared
  def invokeAllTimeout = { e, c -> e.invokeAll([(Callable) c], 10, TimeUnit.SECONDS) }
  @Shared
  def invokeAny = { e, c -> e.invokeAny([(Callable) c]) }
  @Shared
  def invokeAnyTimeout = { e, c -> e.invokeAny([(Callable) c], 10, TimeUnit.SECONDS) }
  @Shared
  def invokeForkJoinTask = { e, c -> e.invoke((ForkJoinTask) c) }
  @Shared
  def scheduleRunnable = { e, c -> e.schedule((Runnable) c, 10, TimeUnit.MILLISECONDS) }
  @Shared
  def scheduleCallable = { e, c -> e.schedule((Callable) c, 10, TimeUnit.MILLISECONDS) }

  def "#poolImpl '#name' same job"() {
    setup:
    def pool = poolImpl
    def m = method
    List<JavaAsyncChild> children = new ArrayList<>()
//    List<Future> jobFutures = new ArrayList<>()

    def count = 20
    JavaAsyncChild child = new JavaAsyncChild(true, false) {
      @Override
      void runImpl() {
        io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace("child") {
          Thread.sleep(10)
        }
//        super.run()
      }
    }
    for (int i = 0; i < count; ++i) {
      io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace("parent") {
//        children.add(child)
        m(pool, child)
//        jobFutures.add(f)
      }
    }
/*
    for (Future f : jobFutures) {
      f.get()
    }
*/
/*
    for (JavaAsyncChild asyncChild : children) {
      asyncChild.waitForCompletion()
    }
*/
    pool.shutdown()

    expect:
//    waitForTraces(count).size() == count
    assertTraces(count) {
      for (int i = 0; i < count; i++) {
        trace(i, 2) {
          io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan(it, 0, "parent")
          io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan(it, 1, "child", span(0))
        }
      }
    }

    where:
    name                | method           | poolImpl
//    "execute Runnable"  | executeRunnable  | Executors.newFixedThreadPool(4)
//    "submit Runnable"   | submitRunnable   | Executors.newFixedThreadPool(4)
//    "submit Callable"   | submitCallable   | Executors.newFixedThreadPool(4)

    // Scheduled executor has additional methods and also may get disabled because it wraps tasks
//    "submit Runnable"   | submitRunnable   | new ScheduledThreadPoolExecutor(1)
//    "submit Callable"   | submitCallable   | new ScheduledThreadPoolExecutor(1)
//    "schedule Runnable" | scheduleRunnable | new ScheduledThreadPoolExecutor(1)
//    "schedule Callable" | scheduleCallable | new ScheduledThreadPoolExecutor(1)

    // ForkJoinPool has additional set of method overloads for ForkJoinTask to deal with
    "submit Runnable"   | submitRunnable   | new ForkJoinPool(4)
    "submit Callable"   | submitCallable   | new ForkJoinPool(4)
  }
}
