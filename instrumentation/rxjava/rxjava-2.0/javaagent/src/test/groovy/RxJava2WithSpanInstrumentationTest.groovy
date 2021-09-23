/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava2.TracedWithSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.processors.UnicastProcessor
import io.reactivex.subjects.CompletableSubject
import io.reactivex.subjects.MaybeSubject
import io.reactivex.subjects.SingleSubject
import io.reactivex.subjects.UnicastSubject
import io.reactivex.subscribers.TestSubscriber
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.StatusCode.ERROR

class RxJava2WithSpanInstrumentationTest extends AgentInstrumentationSpecification {

  def "should capture span for already completed Completable"() {
    setup:
    def observer = new TestObserver()
    def source = Completable.complete()
    new TracedWithSpan()
      .completable(source)
      .subscribe(observer)
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Completable"() {
    setup:
    def source = CompletableSubject.create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .completable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onComplete()
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Completable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def observer = new TestObserver()
    def source = Completable.error(error)
    new TracedWithSpan()
      .completable(source)
      .subscribe(observer)
    observer.assertError(error)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored Completable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = CompletableSubject.create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .completable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled Completable"() {
    setup:
    def source = CompletableSubject.create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .completable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.cancel()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for already completed Maybe"() {
    setup:
    def observer = new TestObserver()
    def source = Maybe.just("Value")
    new TracedWithSpan()
      .maybe(source)
      .subscribe(observer)
    observer.assertValue("Value")
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.maybe"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already empty Maybe"() {
    setup:
    def observer = new TestObserver()
    def source = Maybe.<String> empty()
    new TracedWithSpan()
      .maybe(source)
      .subscribe(observer)
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.maybe"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Maybe"() {
    setup:
    def source = MaybeSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .maybe(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onSuccess("Value")
    observer.assertValue("Value")
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.maybe"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Maybe"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def observer = new TestObserver()
    def source = Maybe.<String> error(error)
    new TracedWithSpan()
      .maybe(source)
      .subscribe(observer)
    observer.assertError(error)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.maybe"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored Maybe"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = MaybeSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .maybe(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.maybe"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled Maybe"() {
    setup:
    def source = MaybeSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .maybe(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.cancel()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.maybe"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for already completed Single"() {
    setup:
    def observer = new TestObserver()
    def source = Single.just("Value")
    new TracedWithSpan()
      .single(source)
      .subscribe(observer)
    observer.assertValue("Value")
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.single"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Single"() {
    setup:
    def source = SingleSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .single(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onSuccess("Value")
    observer.assertValue("Value")
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.single"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Single"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def observer = new TestObserver()
    def source = Single.<String> error(error)
    new TracedWithSpan()
      .single(source)
      .subscribe(observer)
    observer.assertError(error)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.single"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored Single"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = SingleSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .single(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.single"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled Single"() {
    setup:
    def source = SingleSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .single(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.cancel()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.single"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for already completed Observable"() {
    setup:
    def observer = new TestObserver()
    def source = Observable.<String> just("Value")
    new TracedWithSpan()
      .observable(source)
      .subscribe(observer)
    observer.assertValue("Value")
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.observable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Observable"() {
    setup:
    def source = UnicastSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .observable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onComplete()
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.observable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Observable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def observer = new TestObserver()
    def source = Observable.<String> error(error)
    new TracedWithSpan()
      .observable(source)
      .subscribe(observer)
    observer.assertError(error)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.observable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored Observable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = UnicastSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .observable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.observable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled Observable"() {
    setup:
    def source = UnicastSubject.<String> create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .observable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.cancel()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.observable"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for already completed Flowable"() {
    setup:
    def observer = new TestSubscriber()
    def source = Flowable.<String> just("Value")
    new TracedWithSpan()
      .flowable(source)
      .subscribe(observer)
    observer.assertValue("Value")
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flowable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Flowable"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .flowable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onComplete()
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flowable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored Flowable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def observer = new TestSubscriber()
    def source = Flowable.<String> error(error)
    new TracedWithSpan()
      .flowable(source)
      .subscribe(observer)
    observer.assertError(error)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flowable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored Flowable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = UnicastProcessor.<String> create()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .flowable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flowable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled Flowable"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .flowable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.dispose()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.flowable"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for already completed ParallelFlowable"() {
    setup:
    def observer = new TestSubscriber()
    def source = Flowable.<String> just("Value")
    new TracedWithSpan()
      .parallelFlowable(source.parallel())
      .sequential()
      .subscribe(observer)
    observer.assertValue("Value")
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.parallelFlowable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed ParallelFlowable"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .parallelFlowable(source.parallel())
      .sequential()
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onComplete()
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.parallelFlowable"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already errored ParallelFlowable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def observer = new TestSubscriber()
    def source = Flowable.<String> error(error)
    new TracedWithSpan()
      .parallelFlowable(source.parallel())
      .sequential()
      .subscribe(observer)
    observer.assertError(error)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.parallelFlowable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored ParallelFlowable"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = UnicastProcessor.<String> create()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .parallelFlowable(source.parallel())
      .sequential()
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.parallelFlowable"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled ParallelFlowable"() {
    setup:
    def source = UnicastProcessor.<String> create()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .parallelFlowable(source.parallel())
      .sequential()
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onNext("Value")
    observer.assertValue("Value")

    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.cancel()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.parallelFlowable"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Publisher"() {
    setup:
    def source = new CustomPublisher()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .publisher(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onComplete()
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.publisher"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually errored Publisher"() {
    setup:
    def error = new IllegalArgumentException("Boom")
    def source = new CustomPublisher()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .publisher(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onError(error)
    observer.assertError(error)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.publisher"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for canceled Publisher"() {
    setup:
    def source = new CustomPublisher()
    def observer = new TestSubscriber()
    new TracedWithSpan()
      .publisher(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    observer.cancel()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.publisher"
          kind INTERNAL
          hasNoParent()
          attributes {
            "rxjava.canceled" true
          }
        }
      }
    }
  }

  static class CustomPublisher implements Publisher<String>, Subscription {
    Subscriber<? super String> subscriber

    @Override
    void subscribe(Subscriber<? super String> subscriber) {
      this.subscriber = subscriber
      subscriber.onSubscribe(this)
    }

    void onComplete() {
      this.subscriber.onComplete()
    }

    void onError(Throwable exception) {
      this.subscriber.onError(exception)
    }

    @Override
    void request(long l) {}

    @Override
    void cancel() {}
  }
}
