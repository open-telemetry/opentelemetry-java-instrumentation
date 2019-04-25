import com.netflix.hystrix.HystrixObservable
import com.netflix.hystrix.HystrixObservableCommand
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import io.opentracing.tag.Tags
import rx.Observable

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class HystrixObservableTest extends AgentTestRunner {
  // Uncomment for debugging:
  // static {
  //  System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  // }

  def "test command #action"() {
    setup:
    def command = new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
      @Trace
      private String tracedMethod() {
        return "Hello!"
      }

      @Override
      protected Observable<String> construct() {
        Observable.defer {
          Observable.just(tracedMethod())
        }
      }
    }

    def result = runUnderTrace("parent") {
      operation(command)
    }

    expect:
    TRANSFORMED_CLASSES.contains("HystrixObservableTest\$1")
    result == "Hello!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "parent"
          resourceName "parent"
          spanType null
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "hystrix.cmd"
          resourceName "ExampleGroup.HystrixObservableTest\$1.execute"
          spanType null
          childOf span(0)
          errored false
          tags {
            "hystrix.command" "HystrixObservableTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
            "$Tags.COMPONENT.key" "hystrix"
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName "HystrixObservableTest\$1.tracedMethod"
          resourceName "HystrixObservableTest\$1.tracedMethod"
          spanType null
          childOf span(1)
          errored false
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
      }
    }

    where:
    action          | operation
    "toObservable"  | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "observe"       | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe block" | { HystrixObservable cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      cmd.observe().subscribe { next ->
        queue.put(next)
      }
      queue.take()
    }
  }

  def "test command #action fallback"() {
    setup:
    def command = new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
      @Override
      protected Observable<String> construct() {
        Observable.defer {
          Observable.error(new IllegalArgumentException())
        }
      }

      protected Observable<String> resumeWithFallback() {
        return Observable.just("Fallback!")
      }
    }

    def result = runUnderTrace("parent") {
      operation(command)
    }

    expect:
    TRANSFORMED_CLASSES.contains("HystrixObservableTest\$2")
    result == "Fallback!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "parent"
          resourceName "parent"
          spanType null
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "hystrix.cmd"
          resourceName "ExampleGroup.HystrixObservableTest\$2.execute"
          spanType null
          childOf span(0)
          errored true
          tags {
            "hystrix.command" "HystrixObservableTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
            "$Tags.COMPONENT.key" "hystrix"
            errorTags(IllegalArgumentException)
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName "hystrix.cmd"
          resourceName "ExampleGroup.HystrixObservableTest\$2.fallback"
          spanType null
          childOf span(1)
          errored false
          tags {
            "hystrix.command" "HystrixObservableTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
            "$Tags.COMPONENT.key" "hystrix"
            defaultTags()
          }
        }
      }
    }

    where:
    action          | operation
    "toObservable"  | { HystrixObservable cmd -> cmd.toObservable().toBlocking().first() }
    "observe"       | { HystrixObservable cmd -> cmd.observe().toBlocking().first() }
    "observe block" | { HystrixObservable cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      cmd.observe().subscribe { next ->
        queue.put(next)
      }
      queue.take()
    }
  }
}
