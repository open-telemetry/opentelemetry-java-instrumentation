import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Shared

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTraceWithoutExceptionCatch
import static java.util.concurrent.TimeUnit.MILLISECONDS

class RxJava2Test extends AgentTestRunner {

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

  def "Publisher '#name' test"() {
    when:
    def result = assemblePublisherUnderTrace(publisherSupplier)

    then:
    result == expected
    and:
    assertTraces(1) {
      sortSpansByStart()
      trace(0, workSpans + 2) {
        basicSpan(it, 0, "trace-parent")

        basicSpan(it, 1, "publisher-parent", span(0))
        for (int i = 2; i < workSpans + 2; ++i) {
          basicSpan(it, i, "addOne", span(1))
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
  }

  def "Publisher error '#name' test"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    def thrownException = thrown RuntimeException
    thrownException.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      sortSpansByStart()
      trace(0, 2) {
        basicSpan(it, 0, "trace-parent", null, thrownException)


        // It's important that we don't attach errors at the Reactor level so that we don't
        // impact the spans on reactor integrations such as netty and lettuce, as reactor is
        // more of a context propagation mechanism than something we would be tracking for
        // errors this is ok.
        basicSpan(it, 1, "publisher-parent", span(0))
      }
    }

    where:
    name       | publisherSupplier
    "maybe"    | { -> Maybe.error(new RuntimeException(EXCEPTION_MESSAGE)) }
    "flowable" | { -> Flowable.error(new RuntimeException(EXCEPTION_MESSAGE)) }
  }

  def "Publisher step '#name' test"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    def exception = thrown RuntimeException
    exception.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      sortSpansByStart()
      trace(0, workSpans + 2) {
        basicSpan(it, 0, "trace-parent", null, exception)


        // It's important that we don't attach errors at the Reactor level so that we don't
        // impact the spans on reactor integrations such as netty and lettuce, as reactor is
        // more of a context propagation mechanism than something we would be tracking for
        // errors this is ok.
        basicSpan(it, 1, "publisher-parent", span(0))

        for (int i = 2; i < workSpans + 2; i++) {
          basicSpan(it, i, "addOne", span(1))
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
      trace(0, 2) {
        basicSpan(it, 0, "trace-parent")
        basicSpan(it, 1, "publisher-parent", span(0))
      }
    }

    where:
    name             | publisherSupplier
    "basic maybe"    | { -> Maybe.just(1) }
    "basic flowable" | { -> Flowable.fromIterable([5, 6]) }
  }

  def "Publisher chain spans have the correct parent for '#name'"() {
    when:
    assemblePublisherUnderTrace(publisherSupplier)

    then:
    assertTraces(1) {
      trace(0, workSpans + 2) {
        basicSpan(it, 0, "trace-parent")
        basicSpan(it, 1, "publisher-parent", span(0))

        for (int i = 2; i < workSpans + 2; i++) {
          basicSpan(it, i, "addOne", span(1))
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
        sortSpansByStart()
        basicSpan(it, 0, "trace-parent")
        basicSpan(it, 1, "addOne", span(0))
        basicSpan(it, 2, "addTwo", span(0))
      }
    }
  }

  def assemblePublisherUnderTrace(def publisherSupplier) {
    runUnderTrace("trace-parent") {
      // The "add two" operations below should be children of this span
      runUnderTraceWithoutExceptionCatch("publisher-parent") {
        def publisher = publisherSupplier()

        // Read all data from publisher
        if (publisher instanceof Maybe) {
          return ((Maybe) publisher).blockingGet()
        } else if (publisher instanceof Flowable) {
          return ((Flowable) publisher).toList().blockingGet().toArray({ size -> new Integer[size] })
        }

        throw new RuntimeException("Unknown publisher: " + publisher)
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
        }
        throw new IllegalStateException("Unknown publisher type")
      }
    }

    then:
    assertTraces(1) {
      trace(0, 3 + 2 * workItems) {
        sortSpansByStart()
        basicSpan(it, 0, "trace-parent")

        basicSpan(it, 1, "publisher-parent", span(0))
        basicSpan(it, 2, "intermediate", span(1))

        for (int i = 3; i < 3 + 2 * workItems; i = i + 2) {
          basicSpan(it, i, "addOne", span(1))
          basicSpan(it, i + 1, "addTwo", span(1))
        }
      }
    }

    where:
    name             | workItems | publisherSupplier
    "basic maybe"    | 1         | { -> Maybe.just(1).map(addOne) }
    "basic flowable" | 2         | { -> Flowable.fromIterable([1, 2]).map(addOne) }
  }

  def "Flowables produce the right number of results '#scheduler'"() {
    when:
    List<String> values = Flowable.fromIterable(Arrays.asList(1, 2, 3, 4))
      .parallel()
      .runOn(scheduler)
      .flatMap({ num -> Maybe.just(num.toString() + " on " + Thread.currentThread().getName()).toFlowable() })
      .sequential()
      .toList()
      .blockingGet()

    then:
    values.size() == 4

    where:
    scheduler << [Schedulers.newThread(), Schedulers.computation(), Schedulers.single(), Schedulers.trampoline()]
  }

  def cancelUnderTrace(def publisherSupplier) {
    runUnderTrace("trace-parent") {
      runUnderTraceWithoutExceptionCatch("publisher-parent") {
        def publisher = publisherSupplier()
        if (publisher instanceof Maybe) {
          publisher = publisher.toFlowable()
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
}
