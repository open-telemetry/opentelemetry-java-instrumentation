/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.rxjava2.RxJava2AsyncOperationEndStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.parallel.ParallelFlowable
import io.reactivex.processors.ReplayProcessor
import io.reactivex.processors.UnicastProcessor
import io.reactivex.subjects.CompletableSubject
import io.reactivex.subjects.MaybeSubject
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.SingleSubject
import io.reactivex.subjects.UnicastSubject
import io.reactivex.subscribers.TestSubscriber
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

class RxJava2AsyncOperationEndStrategyTest extends Specification {
  String request = "request"
  String response = "response"

  Instrumenter<String, String> instrumenter

  Context context

  Span span

  def underTest = RxJava2AsyncOperationEndStrategy.create()

  def underTestWithExperimentalAttributes = RxJava2AsyncOperationEndStrategy.builder()
    .setCaptureExperimentalSpanAttributes(true)
    .build()

  void setup() {
    instrumenter = Mock()
    context = Mock()
    span = Mock()
    span.storeInContext(_) >> { callRealMethod() }
  }

  static class CompletableTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Completable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(instrumenter, context, request, Completable.complete(), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(instrumenter, context, request, Completable.error(exception), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = CompletableSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = CompletableSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Completable) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = CompletableSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Completable) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = CompletableSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Completable) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = CompletableSubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Completable) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer1.assertComplete()
      observer2.assertComplete()
      observer3.assertComplete()
    }
  }

  static class MaybeTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Maybe)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, Maybe.just(response), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, response, null)
      observer.assertComplete()
    }

    def "ends span on already empty"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, Maybe.empty(), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, Maybe.error(exception), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onSuccess(response)

      then:
      1 * instrumenter.end(context, request, response, null)
      observer.assertComplete()
    }

    def "ends span when empty"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = MaybeSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = MaybeSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Maybe<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = MaybeSubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Maybe<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * instrumenter._

      when:
      source.onSuccess(response)

      then:
      1 * instrumenter.end(context, request, response, null)
      observer1.assertValue(response)
      observer1.assertComplete()
      observer2.assertValue(response)
      observer2.assertComplete()
      observer3.assertValue(response)
      observer3.assertComplete()
    }
  }

  static class SingleTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Single)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(instrumenter, context, request, Single.just(response), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, response, null)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(instrumenter, context, request, Single.error(exception), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = SingleSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onSuccess(response)

      then:
      1 * instrumenter.end(context, request, response, null)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = SingleSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = SingleSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Single<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = SingleSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Single<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = SingleSubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Single<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * instrumenter._

      when:
      source.onSuccess(response)

      then:
      1 * instrumenter.end(context, request, response, null)
      observer1.assertValue(response)
      observer1.assertComplete()
      observer2.assertValue(response)
      observer2.assertComplete()
      observer3.assertValue(response)
      observer3.assertComplete()
    }
  }

  static class ObservableTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Observable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(instrumenter, context, request, Observable.just(response), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(instrumenter, context, request, Observable.error(exception), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastSubject.create()
      def observer = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Observable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastSubject.create()
      def observer = new TestObserver()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Observable<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = ReplaySubject.create()
      def observer1 = new TestObserver()
      def observer2 = new TestObserver()
      def observer3 = new TestObserver()

      when:
      def result = (Observable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer1.assertComplete()
      observer2.assertComplete()
      observer3.assertComplete()
    }
  }

  static class FlowableTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Flowable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, Flowable.just(response), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, Flowable.error(exception), String)
      result.subscribe(observer)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }

    def "ends span once for multiple subscribers"() {
      given:
      def source = ReplayProcessor.create()
      def observer1 = new TestSubscriber()
      def observer2 = new TestSubscriber()
      def observer3 = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer1)
      result.subscribe(observer2)
      result.subscribe(observer3)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer1.assertComplete()
      observer2.assertComplete()
      observer3.assertComplete()
    }
  }

  static class ParallelFlowableTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(ParallelFlowable)
    }

    def "ends span on already completed"() {
      given:
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(instrumenter, context, request, Flowable.just(response).parallel(), String)
      result.sequential().subscribe(observer)

      then:
      observer.assertComplete()
      1 * instrumenter.end(context, request, null, null)
    }

    def "ends span on already errored"() {
      given:
      def exception = new IllegalStateException()
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(instrumenter, context, request, Flowable.error(exception).parallel(), String)
      result.sequential().subscribe(observer)

      then:
      observer.assertError(exception)
      1 * instrumenter.end(context, request, null, exception)
    }

    def "ends span when completed"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(instrumenter, context, request, source.parallel(), String)
      result.sequential().subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      observer.assertComplete()
      1 * instrumenter.end(context, request, null, null)
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()

      when:
      def result = (ParallelFlowable<?>) underTest.end(instrumenter, context, request, source.parallel(), String)
      result.sequential().subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      observer.assertError(exception)
      1 * instrumenter.end(context, request, null, exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (ParallelFlowable<?>) underTest.end(instrumenter, context, request, source.parallel(), String)
      result.sequential().subscribe(observer)

      then:
      0 * instrumenter._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = UnicastProcessor.create()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (ParallelFlowable<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source.parallel(), String)
      result.sequential().subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      1 * span.setAttribute({ it.getKey() == "rxjava.canceled" }, true)
    }
  }

  static class PublisherTest extends RxJava2AsyncOperationEndStrategyTest {
    def "is supported"() {
      expect:
      underTest.supports(Publisher)
    }

    def "ends span when completed"() {
      given:
      def source = new CustomPublisher()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onComplete()

      then:
      1 * instrumenter.end(context, request, null, null)
      observer.assertComplete()
    }

    def "ends span when errored"() {
      given:
      def exception = new IllegalStateException()
      def source = new CustomPublisher()
      def observer = new TestSubscriber()

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      source.onError(exception)

      then:
      1 * instrumenter.end(context, request, null, exception)
      observer.assertError(exception)
    }

    def "ends span when cancelled"() {
      given:
      def source = new CustomPublisher()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTest.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
      0 * span.setAttribute(_)
    }

    def "ends span when cancelled and capturing experimental span attributes"() {
      given:
      def source = new CustomPublisher()
      def observer = new TestSubscriber()
      def context = span.storeInContext(Context.root())

      when:
      def result = (Flowable<?>) underTestWithExperimentalAttributes.end(instrumenter, context, request, source, String)
      result.subscribe(observer)

      then:
      0 * instrumenter._
      0 * span._

      when:
      observer.cancel()

      then:
      1 * instrumenter.end(context, request, null, null)
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
    void request(long l) {}

    @Override
    void cancel() {}
  }
}
