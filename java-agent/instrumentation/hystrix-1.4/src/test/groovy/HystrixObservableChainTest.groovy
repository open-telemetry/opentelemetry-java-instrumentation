import com.netflix.hystrix.HystrixObservableCommand
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import rx.Observable
import rx.schedulers.Schedulers
import spock.lang.Retry
import spock.lang.Timeout

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

@Retry
@Timeout(5)
class HystrixObservableChainTest extends AgentTestRunner {
  static {
    // Disable so failure testing below doesn't inadvertently change the behavior.
    System.setProperty("hystrix.command.default.circuitBreaker.enabled", "false")

    // Uncomment for debugging:
    // System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  }

  def "test command #action"() {
    setup:

    def result = runUnderTrace("parent") {
      def val = new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
        @Trace
        private String tracedMethod() {
          return "Hello"
        }

        @Override
        protected Observable<String> construct() {
          Observable.defer {
            Observable.just(tracedMethod())
          }
            .subscribeOn(Schedulers.immediate())
        }
      }.toObservable()
        .subscribeOn(Schedulers.io())
        .map {
          it.toUpperCase()
        }.flatMap { str ->
        new HystrixObservableCommand<String>(asKey("OtherGroup")) {
          @Trace
          private String tracedMethod() {
            return "$str!"
          }

          @Override
          protected Observable<String> construct() {
            Observable.defer {
              Observable.just(tracedMethod())
            }
              .subscribeOn(Schedulers.computation())
          }
        }.toObservable()
          .subscribeOn(Schedulers.trampoline())
      }.toBlocking().first()
      return val
    }

    expect:
    result == "HELLO!"

    assertTraces(1) {
      trace(0, 5) {
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
            "$MoreTags.RESOURCE_NAME" "ExampleGroup.HystrixObservableChainTest\$1.execute"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableChainTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(1)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "HystrixObservableChainTest\$1.tracedMethod"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "trace"
          }
        }
        span(3) {
          operationName "hystrix.cmd"
          childOf span(1)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "OtherGroup.HystrixObservableChainTest\$2.execute"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixObservableChainTest\$2"
            "hystrix.group" "OtherGroup"
            "hystrix.circuit-open" false
          }
        }
        span(4) {
          operationName "trace.annotation"
          childOf span(3)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "HystrixObservableChainTest\$2.tracedMethod"
            "$MoreTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }
}
