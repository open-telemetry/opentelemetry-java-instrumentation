import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.SpanData

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

    def result = new Supplier<String>() {
      @Override
      @Trace(operationName = "parent")
      String get() {
        return CompletableFuture.supplyAsync(supplier, pool)
          .thenCompose({ s -> CompletableFuture.supplyAsync(new AppendingSupplier(s), differentPool) })
          .thenApply(function)
          .get()
      }
    }.get()

    TEST_WRITER.waitForTraces(1)
    List<SpanData> trace = TEST_WRITER.traces[0]

    expect:
    result == "abc"

    TEST_WRITER.traces.size() == 1
    trace.size() == 4
    trace.get(0).name == "parent"
    trace.get(1).name == "supplier"
    trace.get(1).parentSpanId == trace.get(0).spanId
    trace.get(2).name == "appendingSupplier"
    trace.get(2).parentSpanId == trace.get(0).spanId
    trace.get(3).name == "function"
    trace.get(3).parentSpanId == trace.get(0).spanId

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
