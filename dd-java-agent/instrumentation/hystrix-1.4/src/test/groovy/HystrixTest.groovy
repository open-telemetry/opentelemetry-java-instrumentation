import com.netflix.hystrix.HystrixCommand
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDTags
import datadog.trace.api.Trace
import datadog.trace.instrumentation.api.Tags
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

@Timeout(10)
class HystrixTest extends AgentTestRunner {
  static {
    // Disable so failure testing below doesn't inadvertently change the behavior.
    System.setProperty("hystrix.command.default.circuitBreaker.enabled", "false")

    // Uncomment for debugging:
    // System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  }

  def "test command #action"() {
    setup:
    def command = new HystrixCommand<String>(asKey("ExampleGroup")) {
      @Override
      protected String run() throws Exception {
        return tracedMethod()
      }

      @Trace
      private String tracedMethod() {
        return "Hello!"
      }
    }
    def result = runUnderTrace("parent") {
      def res = operation(command)
      // child spans are reported asynchronously, this is needed for consistent span ordering during test verification
      blockUntilChildSpansFinished(2)
      res
    }
    expect:
    TRANSFORMED_CLASSES.contains("com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler\$ThreadPoolWorker")
    TRANSFORMED_CLASSES.contains("HystrixTest\$1")
    result == "Hello!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored false
          tags {
            "$DDTags.SPAN_TYPE" null
          }
        }
        span(1) {
          operationName "hystrix.cmd"
          childOf span(0)
          errored false
          tags {
            "$DDTags.RESOURCE_NAME" "ExampleGroup.HystrixTest\$1.execute"
            "$DDTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
        span(2) {
          operationName "trace.annotation"
          childOf span(1)
          errored false
          tags {
            "$DDTags.RESOURCE_NAME" "HystrixTest\$1.tracedMethod"
            "$DDTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    action          | operation
    "execute"       | { HystrixCommand cmd -> cmd.execute() }
    "queue"         | { HystrixCommand cmd -> cmd.queue().get() }
    "toObservable"  | { HystrixCommand cmd -> cmd.toObservable().toBlocking().first() }
    "observe"       | { HystrixCommand cmd -> cmd.observe().toBlocking().first() }
    "observe block" | { HystrixCommand cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      cmd.observe().subscribe { next ->
        queue.put(next)
      }
      queue.take()
    }
  }

  def "test command #action fallback"() {
    setup:
    def command = new HystrixCommand<String>(asKey("ExampleGroup")) {
      @Override
      protected String run() throws Exception {
        throw new IllegalArgumentException()
      }

      protected String getFallback() {
        return "Fallback!"
      }
    }
    def result = runUnderTrace("parent") {
      def res = operation(command)
      // child spans are reported asynchronously, this is needed for consistent span ordering during test verification
      blockUntilChildSpansFinished(2)
      res
    }
    expect:
    TRANSFORMED_CLASSES.contains("com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler\$ThreadPoolWorker")
    TRANSFORMED_CLASSES.contains("HystrixTest\$2")
    result == "Fallback!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored false
          tags {
            "$DDTags.SPAN_TYPE" null
          }
        }
        span(1) {
          operationName "hystrix.cmd"
          childOf span(0)
          errored true
          tags {
            "$DDTags.RESOURCE_NAME" "ExampleGroup.HystrixTest\$2.execute"
            "$DDTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixTest\$2"
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
            "$DDTags.RESOURCE_NAME" "ExampleGroup.HystrixTest\$2.fallback"
            "$DDTags.SPAN_TYPE" null
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
      }
    }

    where:
    action          | operation
    "execute"       | { HystrixCommand cmd -> cmd.execute() }
    "queue"         | { HystrixCommand cmd -> cmd.queue().get() }
    "toObservable"  | { HystrixCommand cmd -> cmd.toObservable().toBlocking().first() }
    "observe"       | { HystrixCommand cmd -> cmd.observe().toBlocking().first() }
    "observe block" | { HystrixCommand cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      cmd.observe().subscribe { next ->
        queue.put(next)
      }
      queue.take()
    }
  }
}
