/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.tracer.BaseTracer
import io.opentelemetry.instrumentation.rxjava3.RxJava3AsyncSpanEndStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.parallel.ParallelFlowable
import io.reactivex.rxjava3.processors.ReplayProcessor
import io.reactivex.rxjava3.processors.UnicastProcessor
import io.reactivex.rxjava3.subjects.CompletableSubject
import io.reactivex.rxjava3.subjects.MaybeSubject
import io.reactivex.rxjava3.subjects.ReplaySubject
import io.reactivex.rxjava3.subjects.SingleSubject
import io.reactivex.rxjava3.subjects.UnicastSubject
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

class RxJava3AsyncSpanEndStrategyTest extends Specification {
  BaseTracer tracer

  Context context

  Span span

  def underTest = RxJava3AsyncSpanEndStrategy.create()

  def underTestWithExperimentalAttributes = RxJava3AsyncSpanEndStrategy.newBuilder()
    .setCaptureExperimentalSpanAttributes(true)
    .build()

  void setup() {
    tracer = Mock()
    context = Mock()
    span = Mock()
    span.storeInContext(_) >> { callRealMethod() }
  }

  static class CompletableTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Completable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(tracer, context, Completable.complete())
      result.subscribe(observer)

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(tracer, context, Completable.error(exception))
      result.subscribe(observer)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = CompletableSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = CompletableSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = CompletableSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Completable) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = CompletableSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Completable) underTestWithExperimentalAttributes.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = CompletableSubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Completable) underTest.end(tracer, context, source)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer1.assertComplete()
      observer2.assertComplete()
      observer3.assertComplete()
    }
  }

  static class MaybeTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Maybe)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, Maybe.just("Value"))
      result.subscribe(observer)

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span on already empty"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, Maybe.empty())
      result.subscribe(observer)

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, Maybe.error(exception))
      result.subscribe(observer)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onSuccess("Value")

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when empty"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = MaybeSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Maybe<?>) underTestWithExperimentalAttributes.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = MaybeSubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(tracer, context, source)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * tracer._

      when:
      source.onSuccess("Value")

      then:
      1 * tracer.end(context)
      observer1.assertValue("Value")
      observer1.assertComplete()
      observer2.assertValue("Value")
      observer2.assertComplete()
      observer3.assertValue("Value")
      observer3.assertComplete()
    }
  }

  static class SingleTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Single)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(tracer, context, Single.just("Value"))
      result.subscribe(observer)

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(tracer, context, Single.error(exception))
      result.subscribe(observer)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = SingleSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onSuccess("Value")

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = SingleSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = SingleSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Single<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = SingleSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Single<?>) underTestWithExperimentalAttributes.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = SingleSubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(tracer, context, source)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * tracer._

      when:
      source.onSuccess("Value")

      then:
      1 * tracer.end(context)
      observer1.assertValue("Value")
      observer1.assertComplete()
      observer2.assertValue("Value")
      observer2.assertComplete()
      observer3.assertValue("Value")
      observer3.assertComplete()
    }
  }

  static class ObservableTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Observable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(tracer, context, Observable.just("Value"))
      result.subscribe(observer)

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(tracer, context, Observable.error(exception))
      result.subscribe(observer)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Observable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Observable<?>) underTestWithExperimentalAttributes.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.dispose()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = ReplaySubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer1.assertComplete()
      observer2.assertComplete()
      observer3.assertComplete()
    }
  }

  static class FlowableTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Flowable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, Flowable.just("Value"))
      result.subscribe(observer)

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, Flowable.error(exception))
      result.subscribe(observer)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      observer.cancel()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTestWithExperimentalAttributes.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = ReplayProcessor.create()
      def observer1 = new TestSubscriber()
      def observer2 = new TestSubscriber()
      def observer3 = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer1.assertComplete()
      observer2.assertComplete()
      observer3.assertComplete()
    }
  }

  static class ParallelFlowableTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(ParallelFlowable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(tracer, context, Flowable.just("Value").parallel())
      result.sequential().subscribe(observer)

      then:
      observer.assertComplete()
      1 * tracer.end(context)
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(tracer, context, Flowable.error(exception).parallel())
      result.sequential().subscribe(observer)

      then:
      observer.assertError(exception)
      1 * tracer.endExceptionally(context, exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(tracer, context, source.parallel())
      result.sequential().subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      observer.assertComplete()
      1 * tracer.end(context)
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(tracer, context, source.parallel())
      result.sequential().subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      observer.assertError(exception)
      1 * tracer.endExceptionally(context, exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (ParallelFlowable<?>) underTest.end(tracer, context, source.parallel())
      result.sequential().subscribe(observer)

      then:
      0 * tracer._

      when:
      observer.cancel()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (ParallelFlowable<?>) underTestWithExperimentalAttributes.end(tracer, context, source.parallel())
      result.sequential().subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }
  }

  static class PublisherTest extends RxJava3AsyncSpanEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Publisher)
    }

    def "ends span when completed"() {
      given:
      def source = new CustomPublisher()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onComplete()

      then:
      1 * tracer.end(context)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = new CustomPublisher()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      source.onError(exception)

      then:
      1 * tracer.endExceptionally(context, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = new CustomPublisher()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTest.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._

      when:
      observer.cancel()

      then:
      1 * tracer.end(context)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = new CustomPublisher()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTestWithExperimentalAttributes.end(tracer, context, source)
      result.subscribe(observer)

      then:
      0 * tracer._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * tracer.end(context)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }
  }

  static class CustomPublisher implements Publisher<String>, Subscription {
    Subscriber<? super String> subscriber

    @Override
    void subscribe(Subscriber<? super String> subscriber) {
      this.subscriber = subscriber
      subscriber.onSubscribe(this)
    }

    def onComplete() {
      this.subscriber.onComplete()
    }

    def onError(Throwable exception) {
      this.subscriber.onError(exception)
    }

    @Override
    void request(long l) { }

    @Override
    void cancel() { }
  }
}
