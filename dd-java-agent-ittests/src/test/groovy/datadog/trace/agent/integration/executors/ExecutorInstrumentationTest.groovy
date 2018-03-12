package datadog.trace.agent.integration.executors

import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.agent.test.IntegrationTestUtils
import datadog.trace.api.Trace
import datadog.trace.common.writer.ListWriter
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Method
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ExecutorInstrumentationTest extends Specification {
  @Shared
  ListWriter testWriter = new ListWriter()
  @Shared
  DDTracer tracer = new DDTracer(testWriter)
  @Shared
  Method submitMethod
  @Shared
  Method executeMethod

  def setupSpec() {
    IntegrationTestUtils.registerOrReplaceGlobalTracer(tracer)
    testWriter.start()

    executeMethod = Executor.getMethod("execute", Runnable)
    submitMethod = ExecutorService.getMethod("submit", Callable)
  }

  def setup() {
    getTestWriter().close()
  }

  @Unroll
  // more useful name breaks java9 javac
  // def "#poolImpl.getClass().getSimpleName() #method.getName() propagates"()
  def "#poolImpl #method propagates"() {
    setup:
    def pool = poolImpl
    def m = method

    new Runnable(){
      @Override
      @Trace(operationName = "parent")
      void run() {
        // this child will have a span
        m.invoke(pool, new AsyncChild())
        // this child won't
        m.invoke(pool, new AsyncChild(false, false))
      }
    }.run()

    testWriter.waitForTraces(1)
    List<DDSpan> trace = testWriter.get(0)

    expect:
    testWriter.size() == 1
    trace.size() == 2
    trace.get(0).operationName == "parent"
    trace.get(1).operationName == "asyncChild"
    trace.get(1).parentId == trace.get(0).spanId

    cleanup:
    pool?.shutdown()

    // Unfortunately, there's no simple way to test the cross product of methods/pools.
    where:
    poolImpl                                                                                        | method
    new ForkJoinPool()                                                                              | submitMethod
    new ForkJoinPool()                                                                              | executeMethod
    new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))   | submitMethod
    new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))   | executeMethod
    new ScheduledThreadPoolExecutor(1)                                                              | submitMethod
    new ScheduledThreadPoolExecutor(1)                                                              | executeMethod
  }

  @Unroll
  // more useful name breaks java9 javac
  // def "#poolImpl.getClass().getSimpleName() #method.getName() propagates"()
  def "#poolImpl reports after canceled jobs" () {
    setup:
    def pool = poolImpl
    final AsyncChild child = new AsyncChild(true, true)
    List<Future> jobFutures = new ArrayList<Future>()

    new Runnable(){
      @Override
      @Trace(operationName = "parent")
      void run() {
        try {
          for (int i = 0; i < 20; ++ i) {
            Future f = pool.submit((Callable)child)
            jobFutures.add(f)
          }
        } catch (RejectedExecutionException e) {
        }

        for (Future f : jobFutures) {
          f.cancel(false)
        }
        child.unblock()
      }
    }.run()

    testWriter.waitForTraces(1)

    expect:
    testWriter.size() == 1

    where:
    poolImpl                                                                                        | _
    new ForkJoinPool()                                                                              | _
    new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))   | _
    new ScheduledThreadPoolExecutor(1)                                                              | _
  }

  def "scala futures and callbacks"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.traceWithFutureAndCallbacks()
    testWriter.waitForTraces(1)
    List<DDSpan> trace = testWriter.get(0)

    expect:
    trace.size() == expectedNumberOfSpans
    trace[0].operationName == "ScalaConcurrentTests.traceWithFutureAndCallbacks"
    findSpan(trace, "goodFuture").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "badFuture").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "successCallback").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "failureCallback").context().getParentId() == trace[0].context().getSpanId()
  }

  def "scala propagates across futures with no traces"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.tracedAcrossThreadsWithNoTrace()
    testWriter.waitForTraces(1)
    List<DDSpan> trace = testWriter.get(0)

    expect:
    trace.size() == expectedNumberOfSpans
    trace[0].operationName == "ScalaConcurrentTests.tracedAcrossThreadsWithNoTrace"
    findSpan(trace, "callback").context().getParentId() == trace[0].context().getSpanId()
  }

  def "scala either promise completion"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.traceWithPromises()
    testWriter.waitForTraces(1)
    List<DDSpan> trace = testWriter.get(0)

    expect:
    testWriter.size() == 1
    trace.size() == expectedNumberOfSpans
    trace[0].operationName == "ScalaConcurrentTests.traceWithPromises"
    findSpan(trace, "keptPromise").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "keptPromise2").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "brokenPromise").context().getParentId() == trace[0].context().getSpanId()
  }

  def "scala first completed future"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.tracedWithFutureFirstCompletions()
    testWriter.waitForTraces(1)
    List<DDSpan> trace = testWriter.get(0)

    expect:
    testWriter.size() == 1
    trace.size() == expectedNumberOfSpans
    findSpan(trace, "timeout1").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "timeout2").context().getParentId() == trace[0].context().getSpanId()
    findSpan(trace, "timeout3").context().getParentId() == trace[0].context().getSpanId()
  }

  private DDSpan findSpan(List<DDSpan> trace, String opName) {
    for (DDSpan span : trace) {
      if (span.getOperationName() == opName) {
        return span
      }
    }
    return null
  }
}
