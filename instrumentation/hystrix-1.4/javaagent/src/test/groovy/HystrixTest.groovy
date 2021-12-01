/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.netflix.hystrix.HystrixCommand
import com.netflix.hystrix.HystrixCommandProperties
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan

class HystrixTest extends AgentInstrumentationSpecification {

  def "test command #action"() {
    setup:
    def command = new HystrixCommand<String>(setter("ExampleGroup")) {
      @Override
      protected String run() throws Exception {
        return tracedMethod()
      }

      private String tracedMethod() {
        runInternalSpan("tracedMethod")
        return "Hello!"
      }
    }
    def result = runWithSpan("parent") {
      operation(command)
    }
    expect:
    result == "Hello!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "ExampleGroup.HystrixTest\$1.execute"
          childOf span(0)
          attributes {
            "hystrix.command" "HystrixTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit_open" false
          }
        }
        span(2) {
          name "tracedMethod"
          childOf span(1)
          attributes {
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
    def command = new HystrixCommand<String>(setter("ExampleGroup")) {
      @Override
      protected String run() throws Exception {
        throw new IllegalArgumentException()
      }

      protected String getFallback() {
        return "Fallback!"
      }
    }
    def result = runWithSpan("parent") {
      operation(command)
    }
    expect:
    result == "Fallback!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "ExampleGroup.HystrixTest\$2.execute"
          childOf span(0)
          status ERROR
          errorEvent(IllegalArgumentException)
          attributes {
            "hystrix.command" "HystrixTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit_open" false
          }
        }
        span(2) {
          name "ExampleGroup.HystrixTest\$2.fallback"
          childOf span(1)
          attributes {
            "hystrix.command" "HystrixTest\$2"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit_open" false
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

  def setter(String key) {
    def setter = new HystrixCommand.Setter(asKey(key))
    setter.andCommandPropertiesDefaults(new HystrixCommandProperties.Setter()
      .withExecutionTimeoutInMilliseconds(10_000))
    return setter
  }
}
