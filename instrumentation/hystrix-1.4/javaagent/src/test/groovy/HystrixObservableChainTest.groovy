/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.netflix.hystrix.HystrixCommandProperties
import com.netflix.hystrix.HystrixObservableCommand
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import rx.Observable
import rx.schedulers.Schedulers

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey

class HystrixObservableChainTest extends AgentInstrumentationSpecification {

  def "test command #action"() {
    setup:

    def result = runWithSpan("parent") {
      def val = new HystrixObservableCommand<String>(setter("ExampleGroup")) {
        private String tracedMethod() {
          runWithSpan("tracedMethod") {}
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
        new HystrixObservableCommand<String>(setter("OtherGroup")) {
          private String anotherTracedMethod() {
            runWithSpan("anotherTracedMethod") {}
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
          attributes {
          }
        }
        span(1) {
          name "ExampleGroup.HystrixObservableChainTest\$1.execute"
          childOf span(0)
          attributes {
            "hystrix.command" "HystrixObservableChainTest\$1"
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
        span(3) {
          name "OtherGroup.HystrixObservableChainTest\$2.execute"
          childOf span(1)
          attributes {
            "hystrix.command" "HystrixObservableChainTest\$2"
            "hystrix.group" "OtherGroup"
            "hystrix.circuit_open" false
          }
        }
        span(4) {
          name "anotherTracedMethod"
          childOf span(3)
          attributes {
          }
        }
      }
    }
  }

  def setter(String key) {
    def setter = new HystrixObservableCommand.Setter(asKey(key))
    setter.andCommandPropertiesDefaults(new HystrixCommandProperties.Setter()
      .withExecutionTimeoutInMilliseconds(10_000))
    return setter
  }
}
