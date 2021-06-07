/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.TraceUtils
import java.time.Duration
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Unroll

@Unroll
abstract class AbstractReactorCoreTest extends InstrumentationSpecification {

  public static final String EXCEPTION_MESSAGE = "test exception"

  @Shared
  def addOne = { i ->
    addOneFunc(i)
  }

  @Shared
  def addTwo = { i ->
    addTwoFunc(i)
  }

  @Shared
  def throwException = {
    throw new IllegalStateException(EXCEPTION_MESSAGE)
  }

  def "Publisher '#paramName' test"() {
    when:
    def result = runUnderTrace(publisherSupplier)

    then:
    result == expected
    and:
    assertTraces(1) {
      trace(0, workSpans + 2) {
        basicSpan(it, 0, "trace-parent")
        basicSpan(it, 1, "publisher-parent", span(0))

        for (int i = 0; i < workSpans; i++) {
          basicSpan(it, 2 + i, "add one", span(1))
        }
      }
    }

    where:
    paramName             | expected | workSpans | publisherSupplier
    "basic mono"          | 2        | 1         | { -> Mono.just(1).map(addOne) }
    "two operations mono" | 4        | 2         | { -> Mono.just(2).map(addOne).map(addOne) }
    "delayed mono"        | 4        | 1         | { ->
      Mono.just(3).delayElement(Duration.ofMillis(100)).map(addOne)
    }
    "delayed twice mono"  | 6        | 2         | { ->
      Mono.just(4).delayElement(Duration.ofMillis(100)).map(addOne).delayElement(Duration.ofMillis(100)).map(addOne)
    }
    "basic flux"          | [6, 7]   | 2         | { -> Flux.fromIterable([5, 6]).map(addOne) }
    "two operations flux" | [8, 9]   | 4         | { ->
      Flux.fromIterable([6, 7]).map(addOne).map(addOne)
    }
    "delayed flux"        | [8, 9]   | 2         | { ->
      Flux.fromIterable([7, 8]).delayElements(Duration.ofMillis(100)).map(addOne)
    }
    "delayed twice flux"  | [10, 11] | 4         | { ->
      Flux.fromIterable([8, 9]).delayElements(Duration.ofMillis(100)).map(addOne).delayElements(Duration.ofMillis(100)).map(addOne)
    }

    "mono from callable"  | 12       | 2         | { ->
      Mono.fromCallable({ addOneFunc(10) }).map(addOne)
    }
  }

  def "Publisher error '#paramName' test"() {
    when:
    runUnderTrace(publisherSupplier)

    then:
    def exception = thrown RuntimeException
    exception.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "trace-parent"
          status ERROR
          errorEvent(RuntimeException, EXCEPTION_MESSAGE)
          hasNoParent()
        }

        // It's important that we don't attach errors at the Reactor level so that we don't
        // impact the spans on reactor instrumentations such as netty and lettuce, as reactor is
        // more of a context propagation mechanism than something we would be tracking for
        // errors this is ok.
        basicSpan(it, 1, "publisher-parent", span(0))
      }
    }

    where:
    paramName | publisherSupplier
    "mono"    | { -> Mono.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "flux"    | { -> Flux.error(new RuntimeException(EXCEPTION_MESSAGE)) }
  }

  def "Publisher step '#paramName' test"() {
    when:
    runUnderTrace(publisherSupplier)

    then:
    def exception = thrown IllegalStateException
    exception.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      trace(0, workSpans + 2) {
        span(0) {
          name "trace-parent"
          status ERROR
          errorEvent(IllegalStateException, EXCEPTION_MESSAGE)
          hasNoParent()
        }

        // It's important that we don't attach errors at the Reactor level so that we don't
        // impact the spans on reactor instrumentations such as netty and lettuce, as reactor is
        // more of a context propagation mechanism than something we would be tracking for
        // errors this is ok.
        basicSpan(it, 1, "publisher-parent", span(0))

        for (int i = 0; i < workSpans; i++) {
          span(i + 2) {
            name "add one"
            childOf span(1)
            attributes {
            }
          }
        }
      }
    }

    where:
    paramName            | workSpans | publisherSupplier
    "basic mono failure" | 1         | { -> Mono.just(1).map(addOne).map({ throwException() }) }
    "basic flux failure" | 1         | { ->
      Flux.fromIterable([5, 6]).map(addOne).map({ throwException() })
    }
  }

  def "Publisher '#paramName' cancel"() {
    when:
    cancelUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "trace-parent"
          hasNoParent()
          attributes {
          }
        }

        basicSpan(it, 1, "publisher-parent", span(0))
      }
    }

    where:
    paramName    | publisherSupplier
    "basic mono" | { -> Mono.just(1) }
    "basic flux" | { -> Flux.fromIterable([5, 6]) }
  }

  def "Publisher chain spans have the correct parent for '#paramName'"() {
    when:
    runUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, workSpans + 2) {
        span(0) {
          name "trace-parent"
          hasNoParent()
          attributes {
          }
        }

        basicSpan(it, 1, "publisher-parent", span(0))

        for (int i = 0; i < workSpans; i++) {
          span(i + 2) {
            name "add one"
            childOf span(1)
            attributes {
            }
          }
        }
      }
    }

    where:
    paramName    | workSpans | publisherSupplier
    "basic mono" | 3         | { ->
      Mono.just(1).map(addOne).map(addOne).then(Mono.just(1).map(addOne))
    }
    "basic flux" | 5         | { ->
      Flux.fromIterable([5, 6]).map(addOne).map(addOne).then(Mono.just(1).map(addOne))
    }
  }

  def "Publisher chain spans have the correct parents from assembly time '#paramName'"() {
    when:
    runUnderTrace {
      // The "add one" operations in the publisher created here should be children of the publisher-parent
      Publisher<Integer> publisher = publisherSupplier()

      def tracer = GlobalOpenTelemetry.getTracer("test")
      def intermediate = tracer.spanBuilder("intermediate").startSpan()
      // After this activation, the "add two" operations below should be children of this span
      def scope = Context.current().with(intermediate).makeCurrent()
      try {
        if (publisher instanceof Mono) {
          return ((Mono) publisher).map(addTwo)
        } else if (publisher instanceof Flux) {
          return ((Flux) publisher).map(addTwo)
        }
        throw new IllegalStateException("Unknown publisher type")
      } finally {
        intermediate.end()
        scope.close()
      }
    }

    then:
    assertTraces(1) {
      trace(0, (workItems * 2) + 3) {
        basicSpan(it, 0, "trace-parent")
        basicSpan(it, 1, "publisher-parent", span(0))
        basicSpan(it, 2, "intermediate", span(1))

        for (int i = 0; i < 2 * workItems; i = i + 2) {
          basicSpan(it, 3 + i, "add one", span(1))
          basicSpan(it, 3 + i + 1, "add two", span(1))
        }
      }
    }

    where:
    paramName    | workItems | publisherSupplier
    "basic mono" | 1         | { -> Mono.just(1).map(addOne) }
    "basic flux" | 2         | { -> Flux.fromIterable([1, 2]).map(addOne) }
  }

  def "Nested delayed mono with high concurrency"() {
    setup:
    def iterations = 100
    def remainingIterations = new HashSet<>((0L ..< iterations).toList())

    when:
    (0L ..< iterations).forEach { iteration ->
      def outer = Mono.just("")
        .map({ it })
        .delayElement(Duration.ofMillis(10))
        .map({ it })
        .delayElement(Duration.ofMillis(10))
        .doOnSuccess({
          def middle = Mono.just("")
            .map({ it })
            .delayElement(Duration.ofMillis(10))
            .doOnSuccess({
              TraceUtils.runUnderTrace("inner") {
                Span.current().setAttribute("iteration", iteration)
              }
            })

          TraceUtils.runUnderTrace("middle") {
            Span.current().setAttribute("iteration", iteration)
            middle.subscribe()
          }
        })

      // Context must propagate even if only subscribe is in root span scope
      TraceUtils.runUnderTrace("outer") {
        Span.current().setAttribute("iteration", iteration)
        outer.subscribe()
      }
    }

    then:
    assertTraces(iterations) {
      for (int i = 0; i < iterations; i++) {
        trace(i, 3) {
          long iteration = -1
          span(0) {
            name("outer")
            iteration = span.getAttributes().get(AttributeKey.longKey("iteration")).toLong()
            assert remainingIterations.remove(iteration)
          }
          span(1) {
            name("middle")
            childOf(span(0))
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
          span(2) {
            name("inner")
            childOf(span(1))
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
        }
      }
    }

    assert remainingIterations.isEmpty()
  }

  def "Nested delayed flux with high concurrency"() {
    setup:
    def iterations = 100
    def remainingIterations = new HashSet<>((0L ..< iterations).toList())

    when:
    (0L ..< iterations).forEach { iteration ->
      def outer = Flux.just("a", "b")
        .map({ it })
        .delayElements(Duration.ofMillis(10))
        .map({ it })
        .delayElements(Duration.ofMillis(10))
        .doOnEach({ middleSignal ->
          if (middleSignal.hasValue()) {
            def value = middleSignal.get()

            def middle = Flux.just("c", "d")
              .map({ it })
              .delayElements(Duration.ofMillis(10))
              .doOnEach({ innerSignal ->
                if (innerSignal.hasValue()) {
                  TraceUtils.runUnderTrace("inner " + value + innerSignal.get()) {
                    Span.current().setAttribute("iteration", iteration)
                  }
                }
              })

            TraceUtils.runUnderTrace("middle " + value) {
              Span.current().setAttribute("iteration", iteration)
              middle.subscribe()
            }
          }
        })

      // Context must propagate even if only subscribe is in root span scope
      TraceUtils.runUnderTrace("outer") {
        Span.current().setAttribute("iteration", iteration)
        outer.subscribe()
      }
    }

    then:
    assertTraces(iterations) {
      for (int i = 0; i < iterations; i++) {
        trace(i, 7) {
          long iteration = -1
          String middleA = null
          String middleB = null
          span(0) {
            name("outer")
            iteration = span.getAttributes().get(AttributeKey.longKey("iteration")).toLong()
            assert remainingIterations.remove(iteration)
          }
          span("middle a") {
            middleA = span.spanId
            childOf(span(0))
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
          span("middle b") {
            middleB = span.spanId
            childOf(span(0))
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
          span("inner ac") {
            parentSpanId(middleA)
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
          span("inner ad") {
            parentSpanId(middleA)
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
          span("inner bc") {
            parentSpanId(middleB)
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
          span("inner bd") {
            parentSpanId(middleB)
            assert span.getAttributes().get(AttributeKey.longKey("iteration")) == iteration
          }
        }
      }
    }

    assert remainingIterations.isEmpty()
  }

  def runUnderTrace(def publisherSupplier) {
    TraceUtils.runUnderTrace("trace-parent") {
      def tracer = GlobalOpenTelemetry.getTracer("test")
      def span = tracer.spanBuilder("publisher-parent").startSpan()
      def scope = Context.current().with(span).makeCurrent()
      try {
        def publisher = publisherSupplier()
        // Read all data from publisher
        if (publisher instanceof Mono) {
          return publisher.block()
        } else if (publisher instanceof Flux) {
          return publisher.toStream().toArray({ size -> new Integer[size] })
        }

        throw new IllegalStateException("Unknown publisher: " + publisher)
      } finally {
        span.end()
        scope.close()
      }
    }
  }

  def cancelUnderTrace(def publisherSupplier) {
    TraceUtils.runUnderTrace("trace-parent") {
      def tracer = GlobalOpenTelemetry.getTracer("test")
      def span = tracer.spanBuilder("publisher-parent").startSpan()
      def scope = Context.current().with(span).makeCurrent()

      def publisher = publisherSupplier()
      publisher.subscribe(new Subscriber<Integer>() {
        void onSubscribe(Subscription subscription) {
          subscription.cancel()
        }

        void onNext(Integer t) {
        }

        void onError(Throwable error) {
        }

        void onComplete() {
        }
      })

      span.end()
      scope.close()
    }
  }

  static addOneFunc(int i) {
    runInternalSpan("add one")
    return i + 1
  }

  static addTwoFunc(int i) {
    runInternalSpan("add two")
    return i + 2
  }
}
