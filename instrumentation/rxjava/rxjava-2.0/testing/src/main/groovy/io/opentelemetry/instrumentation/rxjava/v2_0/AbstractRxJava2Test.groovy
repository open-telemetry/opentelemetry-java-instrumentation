/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0

import com.google.common.collect.Lists
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.internal.operators.flowable.FlowablePublish
import io.reactivex.internal.operators.observable.ObservablePublish
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Shared
import spock.lang.Unroll

import static java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * <p>Tests in this class may seem not exhaustive due to the fact that some classes are converted
 * into others, ie. {@link Completable#toMaybe()}. Fortunately, RxJava2 uses helper classes like
 * {@link io.reactivex.internal.operators.maybe.MaybeFromCompletable} and as a result we
 * can test subscriptions and cancellations correctly.
 */
@Unroll
abstract class AbstractRxJava2Test extends InstrumentationSpecification {

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

  def addOneFunc(int i) {
    runWithSpan("addOne") {
      return i + 1
    }
  }

  def addTwoFunc(int i) {
    runWithSpan("addTwo") {
      return i + 2
    }
  }

  def "Publisher '#testName' test"() {
    when:
    def result = assemblePublisherUnderTrace(publisherSupplier)

    then:
    result == expected
    and:
    assertTraces(1) {
      sortSpansByStartTime()
      trace(0, workSpans + 1) {

        span(0) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        for (int i = 1; i < workSpans + 1; ++i) {
          span(i) {
            name "addOne"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
        }
      }
    }

    where:
    testName                  | expected | workSpans | publisherSupplier
    "basic maybe"             | 2        | 1         | { -> Maybe.just(1).map(addOne) }
    "two operations maybe"    | 4        | 2         | { -> Maybe.just(2).map(addOne).map(addOne) }
    "delayed maybe"           | 4        | 1         | { ->
      Maybe.just(3).delay(100, MILLISECONDS).map(addOne)
    }
    "delayed twice maybe"     | 6        | 2         | { ->
      Maybe.just(4).delay(100, MILLISECONDS).map(addOne).delay(100, MILLISECONDS).map(addOne)
    }
    "basic flowable"          | [6, 7]   | 2         | { ->
      Flowable.fromIterable([5, 6]).map(addOne)
    }
    "two operations flowable" | [8, 9]   | 4         | { ->
      Flowable.fromIterable([6, 7]).map(addOne).map(addOne)
    }
    "delayed flowable"        | [8, 9]   | 2         | { ->
      Flowable.fromIterable([7, 8]).delay(100, MILLISECONDS).map(addOne)
    }
    "delayed twice flowable"  | [10, 11] | 4         | { ->
      Flowable.fromIterable([8, 9]).delay(100, MILLISECONDS).map(addOne).delay(100, MILLISECONDS).map(addOne)
    }
    "maybe from callable"     | 12       | 2         | { ->
      Maybe.fromCallable({ addOneFunc(10) }).map(addOne)
    }
    "basic single"            | 1        | 1         | { -> Single.just(0).map(addOne) }
    "basic observable"        | [1]      | 1         | { -> Observable.just(0).map(addOne) }
    "connectable flowable"    | [1]      | 1         | { ->
      FlowablePublish.just(0).delay(100, MILLISECONDS).map(addOne)
    }
    "connectable observable"  | [1]      | 1         | { ->
      ObservablePublish.just(0).delay(100, MILLISECONDS).map(addOne)
    }
  }

  def "Publisher error '#testName' test"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    def thrownException = thrown RuntimeException
    thrownException.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      sortSpansByStartTime()
      trace(0, 1) {
        // It's important that we don't attach errors at the Reactor level so that we don't
        // impact the spans on reactor integrations such as netty and lettuce, as reactor is
        // more of a context propagation mechanism than something we would be tracking for
        // errors this is ok.
        span(0) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
      }
    }

    where:
    testName      | publisherSupplier
    "maybe"       | { -> Maybe.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "flowable"    | { -> Flowable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "single"      | { -> Single.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "observable"  | { -> Observable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "completable" | { -> Completable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
  }

  def "Publisher step '#testName' test"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    def exception = thrown RuntimeException
    exception.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      sortSpansByStartTime()
      trace(0, workSpans + 1) {
        // It's important that we don't attach errors at the Reactor level so that we don't
        // impact the spans on reactor integrations such as netty and lettuce, as reactor is
        // more of a context propagation mechanism than something we would be tracking for
        // errors this is ok.
        span(0) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        for (int i = 1; i < workSpans + 1; i++) {
          span(i) {
            name "addOne"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
        }
      }
    }

    where:
    testName                 | workSpans | publisherSupplier
    "basic maybe failure"    | 1         | { ->
      Maybe.just(1).map(addOne).map({ throwException() })
    }
    "basic flowable failure" | 1         | { ->
      Flowable.fromIterable([5, 6]).map(addOne).map({ throwException() })
    }
  }

  def "Publisher '#testName' cancel"() {
    when:
    cancelUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
      }
    }

    where:
    testName            | publisherSupplier
    "basic maybe"       | { -> Maybe.just(1) }
    "basic flowable"    | { -> Flowable.fromIterable([5, 6]) }
    "basic single"      | { -> Single.just(1) }
    "basic completable" | { -> Completable.fromCallable({ -> 1 }) }
    "basic observable"  | { -> Observable.just(1) }
  }

  def "Publisher chain spans have the correct parent for '#testName'"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, workSpans + 1) {
        span(0) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        for (int i = 1; i < workSpans + 1; i++) {
          span(i) {
            name "addOne"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
        }
      }
    }

    where:
    testName         | workSpans | publisherSupplier
    "basic maybe"    | 3         | { ->
      Maybe.just(1).map(addOne).map(addOne).concatWith(Maybe.just(1).map(addOne))
    }
    "basic flowable" | 5         | { ->
      Flowable.fromIterable([5, 6]).map(addOne).map(addOne).concatWith(Maybe.just(1).map(addOne).toFlowable())
    }
  }

  def "Publisher chain spans have the correct parents from subscription time"() {
    when:
    def maybe = Maybe.just(42)
      .map(addOne)
      .map(addTwo)

    runWithSpan("trace-parent") {
      maybe.blockingGet()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        sortSpansByStartTime()
        span(0) {
          name "trace-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "addOne"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        span(2) {
          name "addTwo"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "Publisher chain spans have the correct parents from subscription time '#testName'"() {
    when:
    assemblePublisherUnderTrace {
      // The "add one" operations in the publisher created here should be children of the publisher-parent
      def publisher = publisherSupplier()

      runWithSpan("intermediate") {
        if (publisher instanceof Maybe) {
          return ((Maybe) publisher).map(addTwo)
        } else if (publisher instanceof Flowable) {
          return ((Flowable) publisher).map(addTwo)
        } else if (publisher instanceof Single) {
          return ((Single) publisher).map(addTwo)
        } else if (publisher instanceof Observable) {
          return ((Observable) publisher).map(addTwo)
        } else if (publisher instanceof Completable) {
          return ((Completable) publisher).toMaybe().map(addTwo)
        }
        throw new IllegalStateException("Unknown publisher type")
      }
    }

    then:
    assertTraces(1) {
      trace(0, 2 + 2 * workItems) {
        sortSpansByStartTime()
        span(0) {
          name "publisher-parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "intermediate"
          kind SpanKind.INTERNAL
          childOf span(0)
        }

        for (int i = 2; i < 2 + 2 * workItems; i = i + 2) {
          span(i) {
            name "addOne"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
          span(i + 1) {
            name "addTwo"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
        }
      }
    }

    where:
    testName           | workItems | publisherSupplier
    "basic maybe"      | 1         | { -> Maybe.just(1).map(addOne) }
    "basic flowable"   | 2         | { -> Flowable.fromIterable([1, 2]).map(addOne) }
    "basic single"     | 1         | { -> Single.just(1).map(addOne) }
    "basic observable" | 1         | { -> Observable.just(1).map(addOne) }
  }

  def "Flowables produce the right number of results '#scheduler'"() {
    when:
    List<String> values = runWithSpan("flowable root") {
      Flowable.fromIterable([1, 2, 3, 4])
        .parallel()
        .runOn(scheduler)
        .flatMap({ num ->
          Maybe.just(num).map(addOne).toFlowable()
        })
        .sequential()
        .toList()
        .blockingGet()
    }

    then:
    values.size() == 4
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "flowable root"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        for (int i = 1; i < values.size() + 1; i++) {
          span(i) {
            name "addOne"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
        }
      }
    }

    where:
    scheduler << [Schedulers.newThread(), Schedulers.computation(), Schedulers.single(), Schedulers.trampoline()]
  }

  def "test many ongoing trace chains on '#scheduler'"() {
    setup:
    int iterations = 100
    Set<Long> remainingIterations = new HashSet<>((0L..(iterations - 1)).toList())

    when:
    RxJava2ConcurrencyTestHelper.launchAndWait(scheduler, iterations, 60000, testRunner())

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

    where:
    scheduler << [Schedulers.newThread(), Schedulers.computation(), Schedulers.single(), Schedulers.trampoline()]
  }

  def cancelUnderTrace(def publisherSupplier) {
    runUnderTraceWithoutExceptionCatch("publisher-parent") {
      def publisher = publisherSupplier()
      if (publisher instanceof Maybe) {
        publisher = publisher.toFlowable()
      } else if (publisher instanceof Single) {
        publisher = publisher.toFlowable()
      } else if (publisher instanceof Completable) {
        publisher = publisher.toFlowable()
      } else if (publisher instanceof Observable) {
        publisher = publisher.toFlowable(BackpressureStrategy.LATEST)
      }

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
    }
  }

  @SuppressWarnings("unchecked")
  def assemblePublisherUnderTrace(def publisherSupplier) {
    // The "add two" operations below should be children of this span
    runUnderTraceWithoutExceptionCatch("publisher-parent") {
      def publisher = publisherSupplier()

      // Read all data from publisher
      if (publisher instanceof Maybe) {
        return ((Maybe) publisher).blockingGet()
      } else if (publisher instanceof Flowable) {
        return Lists.newArrayList(((Flowable) publisher).blockingIterable())
      } else if (publisher instanceof Single) {
        return ((Single) publisher).blockingGet()
      } else if (publisher instanceof Observable) {
        return Lists.newArrayList(((Observable) publisher).blockingIterable())
      } else if (publisher instanceof Completable) {
        return ((Completable) publisher).toMaybe().blockingGet()
      }

      throw new IllegalStateException("Unknown publisher: " + publisher)
    }
  }

  def runUnderTraceWithoutExceptionCatch(String spanName, Closure c) {
    Span span = openTelemetry.getTracer("test")
      .spanBuilder(spanName)
      .startSpan()
    try {
      return span.makeCurrent().withCloseable {
        c.call()
      }
    } finally {
      span.end()
    }
  }
}
