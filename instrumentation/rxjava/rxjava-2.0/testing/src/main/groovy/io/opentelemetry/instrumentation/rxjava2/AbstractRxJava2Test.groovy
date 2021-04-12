/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTraceWithoutExceptionCatch
import static java.util.concurrent.TimeUnit.MILLISECONDS

import com.google.common.collect.Lists
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

/**
 * <p>Tests in this class may seem not exhaustive due to the fact that some classes are converted
 * into others, ie. {@link Completable#toMaybe()}. Fortunately, RxJava2 uses helper classes like
 * {@link io.reactivex.internal.operators.maybe.MaybeFromCompletable} and as a result we
 * can test subscriptions and cancellations correctly.
 */
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
    throw new RuntimeException(EXCEPTION_MESSAGE)
  }

  static addOneFunc(int i) {
    runUnderTrace("addOne") {
      return i + 1
    }
  }

  static addTwoFunc(int i) {
    runUnderTrace("addTwo") {
      return i + 2
    }
  }

  def "Publisher '#name' test"() {
    when:
    def result = assemblePublisherUnderTrace(publisherSupplier)

    then:
    result == expected
    and:
    assertTraces(1) {
      sortSpansByStartTime()
      trace(0, workSpans + 1) {

        basicSpan(it, 0, "publisher-parent")
        for (int i = 1; i < workSpans + 1; ++i) {
          basicSpan(it, i, "addOne", span(0))
        }
      }
    }

    where:
    name                      | expected | workSpans | publisherSupplier
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

  def "Publisher error '#name' test"() {
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
        basicSpan(it, 0, "publisher-parent")
      }
    }

    where:
    name          | publisherSupplier
    "maybe"       | { -> Maybe.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "flowable"    | { -> Flowable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "single"      | { -> Single.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "observable"  | { -> Observable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "completable" | { -> Completable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
  }

  def "Publisher step '#name' test"() {
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
        basicSpan(it, 0, "publisher-parent")

        for (int i = 1; i < workSpans + 1; i++) {
          basicSpan(it, i, "addOne", span(0))
        }
      }
    }

    where:
    name                     | workSpans | publisherSupplier
    "basic maybe failure"    | 1         | { ->
      Maybe.just(1).map(addOne).map({ throwException() })
    }
    "basic flowable failure" | 1         | { ->
      Flowable.fromIterable([5, 6]).map(addOne).map({ throwException() })
    }
  }

  def "Publisher '#name' cancel"() {
    when:
    cancelUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, 1) {
        basicSpan(it, 0, "publisher-parent")
      }
    }

    where:
    name                | publisherSupplier
    "basic maybe"       | { -> Maybe.just(1) }
    "basic flowable"    | { -> Flowable.fromIterable([5, 6]) }
    "basic single"      | { -> Single.just(1) }
    "basic completable" | { -> Completable.fromCallable({ -> 1 }) }
    "basic observable"  | { -> Observable.just(1) }
  }

  def "Publisher chain spans have the correct parent for '#name'"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, workSpans + 1) {
        basicSpan(it, 0, "publisher-parent")

        for (int i = 1; i < workSpans + 1; i++) {
          basicSpan(it, i, "addOne", span(0))
        }
      }
    }

    where:
    name             | workSpans | publisherSupplier
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

    runUnderTrace("trace-parent") {
      maybe.blockingGet()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        sortSpansByStartTime()
        basicSpan(it, 0, "trace-parent")
        basicSpan(it, 1, "addOne", span(0))
        basicSpan(it, 2, "addTwo", span(0))
      }
    }
  }

  def "Publisher chain spans have the correct parents from subscription time '#name'"() {
    when:
    assemblePublisherUnderTrace {
      // The "add one" operations in the publisher created here should be children of the publisher-parent
      def publisher = publisherSupplier()

      runUnderTrace("intermediate") {
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
        basicSpan(it, 0, "publisher-parent")
        basicSpan(it, 1, "intermediate", span(0))

        for (int i = 2; i < 2 + 2 * workItems; i = i + 2) {
          basicSpan(it, i, "addOne", span(0))
          basicSpan(it, i + 1, "addTwo", span(0))
        }
      }
    }

    where:
    name               | workItems | publisherSupplier
    "basic maybe"      | 1         | { -> Maybe.just(1).map(addOne) }
    "basic flowable"   | 2         | { -> Flowable.fromIterable([1, 2]).map(addOne) }
    "basic single"     | 1         | { -> Single.just(1).map(addOne) }
    "basic observable" | 1         | { -> Observable.just(1).map(addOne) }
  }

  def "Flowables produce the right number of results '#scheduler'"() {
    when:
    List<String> values = runUnderTrace("flowable root") {
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
        basicSpan(it, 0, "flowable root")
        for (int i = 1; i < values.size() + 1; i++) {
          basicSpan(it, i, "addOne", span(0))
        }
      }
    }

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

      throw new RuntimeException("Unknown publisher: " + publisher)
    }
  }
}
