/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.lang.Shared

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ExecutorInstrumentationTest extends AgentInstrumentationSpecification {

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

  def "#poolName '#testName' propagates"() {
    setup:
    def pool = poolImpl
    def m = method

    new Runnable() {
      @Override
      void run() {
        runWithSpan("parent") {
          // this child will have a span
          def child1 = new JavaAsyncChild()
          // this child won't
          def child2 = new JavaAsyncChild(false, false)
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
    if (pool.hasProperty("shutdown")) {
      pool.shutdown()
      pool.awaitTermination(10, TimeUnit.SECONDS)
    }

    // Unfortunately, there's no simple way to test the cross product of methods/pools.
    where:
    testName                 | method              | poolImpl
    "execute Runnable"       | executeRunnable     | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "submit Runnable"        | submitRunnable      | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "submit Callable"        | submitCallable      | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "invokeAll"              | invokeAll           | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "invokeAll with timeout" | invokeAllTimeout    | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "invokeAny"              | invokeAny           | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "invokeAny with timeout" | invokeAnyTimeout    | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))

    // Scheduled executor has additional methods and also may get disabled because it wraps tasks
    "execute Runnable"       | executeRunnable     | new ScheduledThreadPoolExecutor(1)
    "submit Runnable"        | submitRunnable      | new ScheduledThreadPoolExecutor(1)
    "submit Callable"        | submitCallable      | new ScheduledThreadPoolExecutor(1)
    "invokeAll"              | invokeAll           | new ScheduledThreadPoolExecutor(1)
    "invokeAll with timeout" | invokeAllTimeout    | new ScheduledThreadPoolExecutor(1)
    "invokeAny"              | invokeAny           | new ScheduledThreadPoolExecutor(1)
    "invokeAny with timeout" | invokeAnyTimeout    | new ScheduledThreadPoolExecutor(1)
    "schedule Runnable"      | scheduleRunnable    | new ScheduledThreadPoolExecutor(1)
    "schedule Callable"      | scheduleCallable    | new ScheduledThreadPoolExecutor(1)

    // ForkJoinPool has additional set of method overloads for ForkJoinTask to deal with
    "execute Runnable"       | executeRunnable     | new ForkJoinPool()
    "execute ForkJoinTask"   | executeForkJoinTask | new ForkJoinPool()
    "submit Runnable"        | submitRunnable      | new ForkJoinPool()
    "submit Callable"        | submitCallable      | new ForkJoinPool()
    "submit ForkJoinTask"    | submitForkJoinTask  | new ForkJoinPool()
    "invoke ForkJoinTask"    | invokeForkJoinTask  | new ForkJoinPool()
    "invokeAll"              | invokeAll           | new ForkJoinPool()
    "invokeAll with timeout" | invokeAllTimeout    | new ForkJoinPool()
    "invokeAny"              | invokeAny           | new ForkJoinPool()
    "invokeAny with timeout" | invokeAnyTimeout    | new ForkJoinPool()

    // CustomThreadPoolExecutor would normally be disabled except enabled above.
    "execute Runnable"       | executeRunnable     | new CustomThreadPoolExecutor()
    "submit Runnable"        | submitRunnable      | new CustomThreadPoolExecutor()
    "submit Callable"        | submitCallable      | new CustomThreadPoolExecutor()
    "invokeAll"              | invokeAll           | new CustomThreadPoolExecutor()
    "invokeAll with timeout" | invokeAllTimeout    | new CustomThreadPoolExecutor()
    "invokeAny"              | invokeAny           | new CustomThreadPoolExecutor()
    "invokeAny with timeout" | invokeAnyTimeout    | new CustomThreadPoolExecutor()

    // Internal executor used by CompletableFuture
    "execute Runnable"       | executeRunnable     | new CompletableFuture.ThreadPerTaskExecutor()
    poolName = poolImpl.class.simpleName
  }

  def "#poolName '#testName' wrap lambdas"() {
    setup:
    ExecutorService pool = poolImpl
    def m = method
    def w = wrap

    JavaAsyncChild child = new JavaAsyncChild(true, true)
    new Runnable() {
      @Override
      void run() {
        runWithSpan("parent") {
          m(pool, w(child))
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
    pool.shutdown()
    pool.awaitTermination(10, TimeUnit.SECONDS)

    where:
    testName            | method           | wrap                           | poolImpl
    "execute Runnable"  | executeRunnable  | { LambdaGen.wrapRunnable(it) } | new ScheduledThreadPoolExecutor(1)
    "submit Runnable"   | submitRunnable   | { LambdaGen.wrapRunnable(it) } | new ScheduledThreadPoolExecutor(1)
    "submit Callable"   | submitCallable   | { LambdaGen.wrapCallable(it) } | new ScheduledThreadPoolExecutor(1)
    "schedule Runnable" | scheduleRunnable | { LambdaGen.wrapRunnable(it) } | new ScheduledThreadPoolExecutor(1)
    "schedule Callable" | scheduleCallable | { LambdaGen.wrapCallable(it) } | new ScheduledThreadPoolExecutor(1)
    poolName = poolImpl.class.simpleName
  }

  def "#poolName '#testName' reports after canceled jobs"() {
    setup:
    ExecutorService pool = poolImpl
    def m = method
    List<JavaAsyncChild> children = new ArrayList<>()
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
              JavaAsyncChild child = new JavaAsyncChild(false, true)
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
          for (JavaAsyncChild child : children) {
            child.unblock()
          }
        }
      }
    }.run()


    expect:
    waitForTraces(1).size() == 1

    pool.shutdown()
    pool.awaitTermination(10, TimeUnit.SECONDS)

    where:
    testName            | method           | poolImpl
    "submit Runnable"   | submitRunnable   | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    "submit Callable"   | submitCallable   | new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))

    // Scheduled executor has additional methods and also may get disabled because it wraps tasks
    "submit Runnable"   | submitRunnable   | new ScheduledThreadPoolExecutor(1)
    "submit Callable"   | submitCallable   | new ScheduledThreadPoolExecutor(1)
    "schedule Runnable" | scheduleRunnable | new ScheduledThreadPoolExecutor(1)
    "schedule Callable" | scheduleCallable | new ScheduledThreadPoolExecutor(1)

    // ForkJoinPool has additional set of method overloads for ForkJoinTask to deal with
    "submit Runnable"   | submitRunnable   | new ForkJoinPool()
    "submit Callable"   | submitCallable   | new ForkJoinPool()
    poolName = poolImpl.class.simpleName
  }

  static class CustomThreadPoolExecutor extends AbstractExecutorService {
    volatile running = true
    def workQueue = new LinkedBlockingQueue<Runnable>(10)

    def worker = new Runnable() {
      void run() {
        try {
          while (running) {
            def runnable = workQueue.take()
            runnable.run()
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt()
        } catch (Exception e) {
          e.printStackTrace()
        }
      }
    }

    def workerThread = new Thread(worker, "ExecutorTestThread")

    private CustomThreadPoolExecutor() {
      workerThread.start()
    }

    @Override
    void shutdown() {
      running = false
      workerThread.interrupt()
    }

    @Override
    List<Runnable> shutdownNow() {
      running = false
      workerThread.interrupt()
      return []
    }

    @Override
    boolean isShutdown() {
      return !running
    }

    @Override
    boolean isTerminated() {
      return workerThread.isAlive()
    }

    @Override
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      workerThread.join(unit.toMillis(timeout))
      return true
    }

    @Override
    def <T> Future<T> submit(Callable<T> task) {
      def future = newTaskFor(task)
      execute(future)
      return future
    }

    @Override
    def <T> Future<T> submit(Runnable task, T result) {
      def future = newTaskFor(task, result)
      execute(future)
      return future
    }

    @Override
    Future<?> submit(Runnable task) {
      def future = newTaskFor(task, null)
      execute(future)
      return future
    }

    @Override
    def <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
      return super.invokeAll(tasks)
    }

    @Override
    def <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
      return super.invokeAll(tasks)
    }

    @Override
    def <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
      return super.invokeAny(tasks)
    }

    @Override
    def <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return super.invokeAny(tasks)
    }

    @Override
    void execute(Runnable command) {
      workQueue.put(command)
    }
  }
}
