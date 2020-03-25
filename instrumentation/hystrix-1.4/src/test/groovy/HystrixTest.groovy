/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.netflix.hystrix.HystrixCommand
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

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

      private String tracedMethod() {
        TEST_TRACER.spanBuilder("tracedMethod").startSpan().end()
        return "Hello!"
      }
    }
    def result = runUnderTrace("parent") {
      operation(command)
    }
    expect:
    TRANSFORMED_CLASSES_NAMES.contains("HystrixTest\$1")
    result == "Hello!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName "ExampleGroup.HystrixTest\$1.execute"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
        span(2) {
          operationName "tracedMethod"
          childOf span(1)
          errored false
          tags {
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
      operation(command)
    }
    expect:
    TRANSFORMED_CLASSES_NAMES.contains("HystrixTest\$2")
    result == "Fallback!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "parent"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName "ExampleGroup.HystrixTest\$2.execute"
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT" "hystrix"
            "hystrix.command" "HystrixTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
            errorTags(IllegalArgumentException)
          }
        }
        span(2) {
          operationName "ExampleGroup.HystrixTest\$2.fallback"
          childOf span(1)
          errored false
          tags {
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
