import com.netflix.hystrix.HystrixCommand
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.TestUtils.runUnderTrace

class HystrixTest extends AgentTestRunner {
  // Uncomment for debugging:
  // static {
  //  System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  // }

  def "test command #action"() {
    setup:
    def command = new HystrixCommand(asKey("ExampleGroup")) {
      @Override
      protected Object run() throws Exception {
        return tracedMethod()
      }

      @Trace
      private String tracedMethod() {
        return "Hello!"
      }
    }
    def result = runUnderTrace("parent") {
      operation(command)
    }
    expect:
    TRANSFORMED_CLASSES.contains("com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler\$ThreadPoolWorker")
    TRANSFORMED_CLASSES.contains("HystrixTest\$1")
    result == "Hello!"

    assertTraces(TEST_WRITER, 1) {
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
          operationName "HystrixTest\$1.run"
          resourceName "HystrixTest\$1.run"
          spanType null
          childOf span(0)
          errored false
          tags {
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName "HystrixTest\$1.tracedMethod"
          resourceName "HystrixTest\$1.tracedMethod"
          spanType null
          childOf span(1)
          errored false
          tags {
            defaultTags()
          }
        }
      }
    }

    where:
    action    | operation
    "execute" | { HystrixCommand cmd -> cmd.execute() }
    "queue"   | { HystrixCommand cmd -> cmd.queue().get() }
    "observe" | { HystrixCommand cmd -> cmd.observe().toBlocking().first() }
    "observe" | { HystrixCommand cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      cmd.observe().subscribe { next ->
        queue.put(next)
      }
      queue.poll()
    }
  }

  def "test command #action fallback"() {
    setup:
    def command = new HystrixCommand(asKey("ExampleGroup")) {
      @Override
      protected Object run() throws Exception {
        throw new IllegalArgumentException()
      }

      protected String getFallback() {
        return "Fallback!"
      }
    }
    def result = runUnderTrace("parent") {
      operation(command)
    }
    expect:
    TRANSFORMED_CLASSES.contains("com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler\$ThreadPoolWorker")
    TRANSFORMED_CLASSES.contains("HystrixTest\$2")
    result == "Fallback!"

    assertTraces(TEST_WRITER, 1) {
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
          operationName "HystrixTest\$2.getFallback"
          resourceName "HystrixTest\$2.getFallback"
          spanType null
          childOf span(0)
          errored false
          tags {
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName "HystrixTest\$2.run"
          resourceName "HystrixTest\$2.run"
          spanType null
          childOf span(0)
          errored true
          tags {
            errorTags(IllegalArgumentException)
            defaultTags()
          }
        }
      }
    }

    where:
    action    | operation
    "execute" | { HystrixCommand cmd -> cmd.execute() }
    "queue"   | { HystrixCommand cmd -> cmd.queue().get() }
    "observe" | { HystrixCommand cmd -> cmd.observe().toBlocking().first() }
    "observe" | { HystrixCommand cmd ->
      BlockingQueue queue = new LinkedBlockingQueue()
      cmd.observe().subscribe { next ->
        queue.put(next)
      }
      queue.take()
    }
  }
}
