/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier
import spock.lang.Requires

@Requires({ javaVersion >= 1.8 })
class CompletableFutureTest extends AgentTestRunner {

  def "CompletableFuture test"() {
    setup:
    def pool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def differentPool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def supplier = new Supplier<String>() {
      @Override
      String get() {
        getTestTracer().spanBuilder("supplier").startSpan().end()
        sleep(1000)
        return "a"
      }
    }

    def function = new Function<String, String>() {
      @Override
      String apply(String s) {
        getTestTracer().spanBuilder("function").startSpan().end()
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

  def "test supplyAsync"() {
    when:
    CompletableFuture<String> completableFuture = runUnderTrace("parent") {
      def result = CompletableFuture.supplyAsync {
        runUnderTrace("child") {
          "done"
        }
      }
      return result
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "child", span(0))
      }
    }
  }

  def "test thenApply"() {
    when:
    CompletableFuture<String> completableFuture = runUnderTrace("parent") {
      CompletableFuture.supplyAsync {
        "done"
      }.thenApply { result ->
        runUnderTrace("child") {
          result
        }
      }
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "child", span(0))
      }
    }
  }

  def "test thenApplyAsync"() {
    when:
    CompletableFuture<String> completableFuture = runUnderTrace("parent") {
      def result = CompletableFuture.supplyAsync {
        "done"
      }.thenApplyAsync { result ->
        runUnderTrace("child") {
          result
        }
      }
      return result
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "child", span(0))
      }
    }
  }

  def "test thenCompose"() {
    when:
    CompletableFuture<String> completableFuture = runUnderTrace("parent") {
      def result = CompletableFuture.supplyAsync {
        "done"
      }.thenCompose { result ->
        CompletableFuture.supplyAsync {
          runUnderTrace("child") {
            result
          }
        }
      }
      return result
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "child", span(0))
      }
    }
  }

  def "test thenComposeAsync"() {
    when:
    CompletableFuture<String> completableFuture = runUnderTrace("parent") {
      def result = CompletableFuture.supplyAsync {
        "done"
      }.thenComposeAsync { result ->
        CompletableFuture.supplyAsync {
          runUnderTrace("child") {
            result
          }
        }
      }
      return result
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "child", span(0))
      }
    }
  }

  def "test compose and apply"() {
    when:
    CompletableFuture<String> completableFuture = runUnderTrace("parent") {
      def result = CompletableFuture.supplyAsync {
        "do"
      }.thenCompose { result ->
        CompletableFuture.supplyAsync {
          result + "ne"
        }
      }.thenApplyAsync { result ->
        runUnderTrace("child") {
          result
        }
      }
      return result
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "child", span(0))
      }
    }
  }

  static class AppendingSupplier implements Supplier<String> {
    String letter

    AppendingSupplier(String letter) {
      this.letter = letter
    }

    @Override
    String get() {
      getTestTracer().spanBuilder("appendingSupplier").startSpan().end()
      return letter + "b"
    }
  }
}
