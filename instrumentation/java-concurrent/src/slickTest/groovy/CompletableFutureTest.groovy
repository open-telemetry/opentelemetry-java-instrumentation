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

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.data.SpanData
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
      String get() {
        TEST_TRACER.spanBuilder("supplier").startSpan().end()
        sleep(1000)
        return "a"
      }
    }

    def function = new Function<String, String>() {
      @Override
      String apply(String s) {
        TEST_TRACER.spanBuilder("function").startSpan().end()
        return s + "c"
      }
    }

    def result = new Supplier<String>() {
      @Override
      String get() {
        runUnderTrace("parent") {
          return CompletableFuture.supplyAsync(supplier, pool)
            .thenCompose({ s -> CompletableFuture.supplyAsync(new AppendingSupplier(s), differentPool) })
            .thenApply(function)
            .get()
        }
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
    String get() {
      TEST_TRACER.spanBuilder("appendingSupplier").startSpan().end()
      return letter + "b"
    }
  }

}
