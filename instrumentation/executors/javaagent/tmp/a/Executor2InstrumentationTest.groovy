/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.TraceUtils
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
import spock.lang.Shared

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

    def count = 20
    JavaAsyncChild child = new JavaAsyncChild(true, false) {
      @Override
      void runImpl() {
        TraceUtils.runUnderTrace("child") {
          Thread.sleep(10)
        }
      }
    }
    for (int i = 0; i < count; ++i) {
      TraceUtils.runUnderTrace("parent") {
        m(pool, child)
      }
    }

    pool.shutdown()

    expect:
    assertTraces(count) {
      for (int i = 0; i < count; i++) {
        trace(i, 2) {
          TraceUtils.basicSpan(it, 0, "parent")
          TraceUtils.basicSpan(it, 1, "child", span(0))
        }
      }
    }

    where:
    name                | method           | poolImpl
    "execute Runnable"  | executeRunnable  | Executors.newFixedThreadPool(4)
    "submit Runnable"   | submitRunnable   | Executors.newFixedThreadPool(4)
    "submit Callable"   | submitCallable   | Executors.newFixedThreadPool(4)

    // ForkJoinPool has additional set of method overloads for ForkJoinTask to deal with
    "submit Runnable"   | submitRunnable   | new ForkJoinPool(4)
    "submit Callable"   | submitCallable   | new ForkJoinPool(4)
  }
}
