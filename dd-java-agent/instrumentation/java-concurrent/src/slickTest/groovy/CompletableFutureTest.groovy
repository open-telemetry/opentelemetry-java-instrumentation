import datadog.opentracing.DDSpan
import datadog.opentracing.scopemanager.ContinuableScope
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import io.opentracing.util.GlobalTracer

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

/**
 * Note: ideally this should live with the rest of ExecutorInstrumentationTest,
 * but this code needs java8 so we put it here for now.
 */
class CompletableFutureTest extends AgentTestRunner {

  def "CompletableFuture test"() {
    setup:
    def pool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def differentPool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def supplier = new Supplier<String>() {
      @Override
      @Trace(operationName = "supplier")
      String get() {
        sleep(1000)
        return "a"
      }
    }

    def function = new Function<String, String>() {
      @Override
      @Trace(operationName = "function")
      String apply(String s) {
        return s + "c"
      }
    }

    def future = new Supplier<CompletableFuture<String>>() {
      @Override
      @Trace(operationName = "parent")
      CompletableFuture<String> get() {
        ((ContinuableScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true)
        return CompletableFuture.supplyAsync(supplier, pool)
          .thenCompose({ s -> CompletableFuture.supplyAsync(new AppendingSupplier(s), differentPool) })
          .thenApply(function)
      }
    }.get()

    def result = future.get()

    TEST_WRITER.waitForTraces(1)
    List<DDSpan> trace = TEST_WRITER.get(0)

    expect:
    result == "abc"

    TEST_WRITER.size() == 1
    trace.size() == 4
    trace.get(0).operationName == "parent"
    trace.get(1).operationName == "function"
    trace.get(1).parentId == trace.get(0).spanId
    trace.get(2).operationName == "appendingSupplier"
    trace.get(2).parentId == trace.get(0).spanId
    trace.get(3).operationName == "supplier"
    trace.get(3).parentId == trace.get(0).spanId

    cleanup:
    pool?.shutdown()
    differentPool?.shutdown()
  }

  class AppendingSupplier implements Supplier<String> {
    String letter

    AppendingSupplier(String letter) {
      this.letter = letter
    }

    @Override
    @Trace(operationName = "appendingSupplier")
    String get() {
      return letter + "b"
    }
  }

}
