import com.netflix.hystrix.HystrixObservable
import com.netflix.hystrix.HystrixObservableCommand
import com.netflix.hystrix.exception.HystrixRuntimeException
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import rx.Observable
import rx.schedulers.Schedulers
import spock.lang.Retry
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

@Retry
@Timeout(5)
class HystrixObservableTest extends AgentTestRunner {
  static {
    // Disable so failure testing below doesn't inadvertently change the behavior.
    System.setProperty("hystrix.command.default.circuitBreaker.enabled", "false")

    // Uncomment for debugging:
    // System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  }

  def "test command #action"() {
    setup:
    def observeOnFn = observeOn
    def subscribeOnFn = subscribeOn
    def result = runUnderTrace("parent") {
      def val = operation new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
        @Trace
        private String tracedMethod() {
          return "Hello!"
        }

        @Override
        protected Observable<String> construct() {
          def obs = Observable.defer {
            Observable.just(tracedMethod()).repeat(1)
          }
          if (observeOnFn) {
            obs = obs.observeOn(observeOnFn)
          }
          if (subscribeOnFn) {
            obs = obs.subscribeOn(subscribeOnFn)
          }
          return obs
        }
      }
      return val
    }

    expect:
    TRANSFORMED_CLASSES.contains("HystrixObservableTest\$1")
    result == "Hello!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" null
          }
        }
        span(1) {
          operationName "hystrix.cmd"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "ExampleGroup.HystrixObservableTest\$1.execute"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(1)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "HystrixObservableTest\$1.tracedMethod"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    action               | observeOn                | subscribeOn              | operation
    "toObservable"       | null                     | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-I"     | Schedulers.immediate()   | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-T"     | Schedulers.trampoline()  | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-C"     | Schedulers.computation() | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-IO"    | Schedulers.io()          | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-NT"    | Schedulers.newThread()   | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+I"     | null                     | Schedulers.immediate()   | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+T"     | null                     | Schedulers.trampoline()  | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+C"     | null                     | Schedulers.computation() | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+IO"    | null                     | Schedulers.io()          | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+NT"    | null                     | Schedulers.newThread()   | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "observe"            | null                     | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-I"          | Schedulers.immediate()   | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-T"          | Schedulers.trampoline()  | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-C"          | Schedulers.computation() | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-IO"         | Schedulers.io()          | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-NT"         | Schedulers.newThread()   | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+I"          | null                     | Schedulers.immediate()   | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+T"          | null                     | Schedulers.trampoline()  | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+C"          | null                     | Schedulers.computation() | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+IO"         | null                     | Schedulers.io()          | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+NT"         | null                     | Schedulers.newThread()   | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "toObservable block" | Schedulers.computation() | Schedulers.newThread()   | { HystrixObservable cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      def subscription = cmd.toObservable().subscribe { next ->
        queue.put(next)
      }
      def val = queue.take()
      subscription.unsubscribe()
      return val
    }
  }

  def "test command #action fallback"() {
    setup:
    def observeOnFn = observeOn
    def subscribeOnFn = subscribeOn
    def result = runUnderTrace("parent") {
      def val = operation new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
        @Override
        protected Observable<String> construct() {
          def err = Observable.defer {
            Observable.error(new IllegalArgumentException()).repeat(1)
          }
          if (observeOnFn) {
            err = err.observeOn(observeOnFn)
          }
          if (subscribeOnFn) {
            err = err.subscribeOn(subscribeOnFn)
          }
          return err
        }

        protected Observable<String> resumeWithFallback() {
          return Observable.just("Fallback!").repeat(1)
        }
      }
      return val
    }

    expect:
    TRANSFORMED_CLASSES.contains("HystrixObservableTest\$2")
    result == "Fallback!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" null
          }
        }
        span(1) {
          operationName "hystrix.cmd"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "ExampleGroup.HystrixObservableTest\$2.execute"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
            errorTags(IllegalArgumentException)
          }
        }
        span(2) {
          operationName "hystrix.cmd"
          childOf span(1)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "ExampleGroup.HystrixObservableTest\$2.fallback"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
      }
    }

    where:
    action               | observeOn                | subscribeOn              | operation
    "toObservable"       | null                     | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-I"     | Schedulers.immediate()   | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-T"     | Schedulers.trampoline()  | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-C"     | Schedulers.computation() | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-IO"    | Schedulers.io()          | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-NT"    | Schedulers.newThread()   | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+I"     | null                     | Schedulers.immediate()   | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+T"     | null                     | Schedulers.trampoline()  | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+C"     | null                     | Schedulers.computation() | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+IO"    | null                     | Schedulers.io()          | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+NT"    | null                     | Schedulers.newThread()   | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "observe"            | null                     | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-I"          | Schedulers.immediate()   | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-T"          | Schedulers.trampoline()  | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-C"          | Schedulers.computation() | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-IO"         | Schedulers.io()          | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-NT"         | Schedulers.newThread()   | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+I"          | null                     | Schedulers.immediate()   | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+T"          | null                     | Schedulers.trampoline()  | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+C"          | null                     | Schedulers.computation() | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+IO"         | null                     | Schedulers.io()          | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+NT"         | null                     | Schedulers.newThread()   | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "toObservable block" | null                     | null                     | { HystrixObservable cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      def subscription = cmd.toObservable().subscribe { next ->
        queue.put(next)
      }
      def val = queue.take()
      subscription.unsubscribe()
      return val
    }
  }

  def "test no fallback results in error for #action"() {
    setup:
    def observeOnFn = observeOn
    def subscribeOnFn = subscribeOn

    when:
    runUnderTrace("parent") {
      operation new HystrixObservableCommand<String>(asKey("FailingGroup")) {

        @Override
        protected Observable<String> construct() {
          def err = Observable.defer {
            Observable.error(new IllegalArgumentException())
          }
          if (observeOnFn) {
            err = err.observeOn(observeOnFn)
          }
          if (subscribeOnFn) {
            err = err.subscribeOn(subscribeOnFn)
          }
          return err
        }
      }
    }

    then:
    TRANSFORMED_CLASSES.contains("HystrixObservableTest\$3")
    def err = thrown HystrixRuntimeException
    err.cause instanceof IllegalArgumentException

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" null
            errorTags(HystrixRuntimeException, "HystrixObservableTest\$3 failed and no fallback available.")
          }
        }
        span(1) {
          operationName "hystrix.cmd"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "FailingGroup.HystrixObservableTest\$3.execute"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableTest\$3"
            "hystrix.group" "FailingGroup"
            "hystrix.circuit-open" false
            errorTags(IllegalArgumentException)
          }
        }
        span(2) {
          operationName "hystrix.cmd"
          childOf span(1)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "FailingGroup.HystrixObservableTest\$3.fallback"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableTest\$3"
            "hystrix.group" "FailingGroup"
            "hystrix.circuit-open" false
            errorTags(UnsupportedOperationException, "No fallback available.")
          }
        }
      }
    }

    where:
    action               | observeOn                | subscribeOn              | operation
    "toObservable"       | null                     | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-I"     | Schedulers.immediate()   | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-T"     | Schedulers.trampoline()  | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-C"     | Schedulers.computation() | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-IO"    | Schedulers.io()          | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable-NT"    | Schedulers.newThread()   | null                     | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+I"     | null                     | Schedulers.immediate()   | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+T"     | null                     | Schedulers.trampoline()  | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+C"     | null                     | Schedulers.computation() | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+IO"    | null                     | Schedulers.io()          | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "toObservable+NT"    | null                     | Schedulers.newThread()   | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "observe"            | null                     | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-I"          | Schedulers.immediate()   | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-T"          | Schedulers.trampoline()  | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-C"          | Schedulers.computation() | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-IO"         | Schedulers.io()          | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe-NT"         | Schedulers.newThread()   | null                     | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+I"          | null                     | Schedulers.immediate()   | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+T"          | null                     | Schedulers.trampoline()  | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+C"          | null                     | Schedulers.computation() | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+IO"         | null                     | Schedulers.io()          | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe+NT"         | null                     | Schedulers.newThread()   | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "toObservable block" | Schedulers.computation() | Schedulers.newThread()   | { HystrixObservable cmd ->
      def queue = new LinkedBlockingQueue<Throwable>()
      def subscription = cmd.toObservable().subscribe({ next ->
        queue.put(new Exception("Unexpectedly got a next"))
      }, { next ->
        queue.put(next)
      })
      Throwable ex = queue.take()
      subscription.unsubscribe()
      throw ex
    }
  }
}
