/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.netflix.hystrix.HystrixObservableCommand
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.ConfigUtils
import rx.Observable
import rx.schedulers.Schedulers

class HystrixObservableChainTest extends AgentTestRunner {
  static {
    // Disable so failure testing below doesn't inadvertently change the behavior.
    System.setProperty("hystrix.command.default.circuitBreaker.enabled", "false")

    // Uncomment for debugging:
    // System.setProperty("hystrix.command.default.execution.timeout.enabled", "false")
  }

  static final PREVIOUS_CONFIG = ConfigUtils.updateConfig {
    it.setProperty("otel.hystrix.tags.enabled", "true")
  }

  def cleanupSpec() {
    ConfigUtils.setConfig(PREVIOUS_CONFIG)
  }

  def "test command #action"() {
    setup:

    def result = runUnderTrace("parent") {
      def val = new HystrixObservableCommand<String>(asKey("ExampleGroup")) {
        private String tracedMethod() {
          getTestTracer().spanBuilder("tracedMethod").startSpan().end()
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
          private String anotherTracedMethod() {
            getTestTracer().spanBuilder("anotherTracedMethod").startSpan().end()
            return "$str!"
          }

          @Override
          protected Observable<String> construct() {
            Observable.defer {
              Observable.just(anotherTracedMethod())
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
          name "parent"
          hasNoParent()
          errored false
          attributes {
          }
        }
        span(1) {
          name "ExampleGroup.HystrixObservableChainTest\$1.execute"
          childOf span(0)
          errored false
          attributes {
            "hystrix.command" "HystrixObservableChainTest\$1"
            "hystrix.group" "ExampleGroup"
            "hystrix.circuit-open" false
          }
        }
        span(2) {
          name "tracedMethod"
          childOf span(1)
          errored false
          attributes {
          }
        }
        span(3) {
          name "OtherGroup.HystrixObservableChainTest\$2.execute"
          childOf span(1)
          errored false
          attributes {
            "hystrix.command" "HystrixObservableChainTest\$2"
            "hystrix.group" "OtherGroup"
            "hystrix.circuit-open" false
          }
        }
        span(4) {
          name "anotherTracedMethod"
          childOf span(3)
          errored false
          attributes {
          }
        }
      }
    }
  }
}
