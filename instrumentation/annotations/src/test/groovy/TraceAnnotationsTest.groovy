/*
 * Copyright The OpenTelemetry Authors
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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.test.annotation.SayTracedHello
import io.opentracing.contrib.dropwizard.Trace

import java.util.concurrent.Callable

class TraceAnnotationsTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.annotations")
    }
  }

  def "test simple case annotations"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHello()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SayTracedHello.sayHello"
          parent()
          errored false
          attributes {
            "myattr" "test"
          }
        }
      }
    }
  }

  def "test complex case annotations"() {
    when:
    // Test new trace with 2 children spans
    SayTracedHello.sayHELLOsayHA()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "SayTracedHello.sayHELLOsayHA"
          parent()
          errored false
          attributes {
            "myattr" "test2"
          }
        }
        span(1) {
          operationName "SayTracedHello.sayHello"
          childOf span(0)
          errored false
          attributes {
            "myattr" "test"
          }
        }
        span(2) {
          operationName "SayTracedHello.sayHello"
          childOf span(0)
          errored false
          attributes {
            "myattr" "test"
          }
        }
      }
    }
  }

  def "test exception exit"() {
    setup:
    Throwable error = null
    try {
      SayTracedHello.sayERROR()
    } catch (final Throwable ex) {
      error = ex
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SayTracedHello.sayERROR"
          errored true
          errorEvent(error.class)
        }
      }
    }
  }

  def "test annonymous class annotations"() {
    setup:
    // Test anonymous classes with package.
    SayTracedHello.fromCallable()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SayTracedHello\$1.call"
          attributes {
          }
        }
      }
    }

    when:
    // Test anonymous classes with no package.
    new Callable<String>() {
      @Trace
      @Override
      String call() throws Exception {
        return "Howdy!"
      }
    }.call()

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "SayTracedHello\$1.call"
          attributes {
          }
        }
        trace(1, 1) {
          span(0) {
            operationName "TraceAnnotationsTest\$1.call"
            attributes {
            }
          }
        }
      }
    }
  }
}
