/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.lang.Requires

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

@Requires({ javaVersion >= 1.8 })
class CompletableFutureTest extends AgentInstrumentationSpecification {

  def "CompletableFuture test"() {
    setup:
    def pool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def differentPool = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1))
    def supplier = new Supplier<String>() {
      @Override
      String get() {
        runWithSpan("supplier") {}
        sleep(1000)
        return "a"
      }
    }

    def function = new Function<String, String>() {
      @Override
      String apply(String s) {
        runWithSpan("function") {}
        return s + "c"
      }
    }

    def result = new Supplier<String>() {
      @Override
      String get() {
        runWithSpan("parent") {
          return CompletableFuture.supplyAsync(supplier, pool)
            .thenCompose({ s -> CompletableFuture.supplyAsync(new AppendingSupplier(s), differentPool) })
            .thenApply(function)
            .get()
        }
      }
    }.get()

    expect:
    result == "abc"

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "supplier"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        span(2) {
          name "appendingSupplier"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        span(3) {
          name "function"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    cleanup:
    pool?.shutdown()
    differentPool?.shutdown()
  }

  def "test supplyAsync"() {
    when:
    CompletableFuture<String> completableFuture = runWithSpan("parent") {
      def result = CompletableFuture.supplyAsync {
        runWithSpan("child") {
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
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test thenApply"() {
    when:
    CompletableFuture<String> completableFuture = runWithSpan("parent") {
      CompletableFuture.supplyAsync {
        "done"
      }.thenApply { result ->
        runWithSpan("child") {
          result
        }
      }
    }

    then:
    completableFuture.get() == "done"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test thenApplyAsync"() {
    when:
    CompletableFuture<String> completableFuture = runWithSpan("parent") {
      def result = CompletableFuture.supplyAsync {
        "done"
      }.thenApplyAsync { result ->
        runWithSpan("child") {
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
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test thenCompose"() {
    when:
    CompletableFuture<String> completableFuture = runWithSpan("parent") {
      def result = CompletableFuture.supplyAsync {
        "done"
      }.thenCompose { result ->
        CompletableFuture.supplyAsync {
          runWithSpan("child") {
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
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test thenComposeAsync"() {
    when:
    CompletableFuture<String> completableFuture = runWithSpan("parent") {
      def result = CompletableFuture.supplyAsync {
        "done"
      }.thenComposeAsync { result ->
        CompletableFuture.supplyAsync {
          runWithSpan("child") {
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
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test compose and apply"() {
    when:
    CompletableFuture<String> completableFuture = runWithSpan("parent") {
      def result = CompletableFuture.supplyAsync {
        "do"
      }.thenCompose { result ->
        CompletableFuture.supplyAsync {
          result + "ne"
        }
      }.thenApplyAsync { result ->
        runWithSpan("child") {
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
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  class AppendingSupplier implements Supplier<String> {
    String letter

    AppendingSupplier(String letter) {
      this.letter = letter
    }

    @Override
    String get() {
      runWithSpan("appendingSupplier") {}
      return letter + "b"
    }
  }
}
