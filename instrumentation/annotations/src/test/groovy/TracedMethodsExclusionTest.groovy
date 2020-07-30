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

import io.opentracing.contrib.dropwizard.Trace
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils

class TracedMethodsExclusionTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("otel.trace.methods", "${TestClass.name}[included,excluded]")
      System.setProperty("otel.trace.methods.exclude", "${TestClass.name}[excluded,annotatedButExcluded]")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.methods")
      System.clearProperty("otel.trace.methods.exclude")
    }
  }

  static class TestClass {
    //This method is configured to be traced
    String included() {
      return "Hello!"
    }

    //This method is not mentioned in any configuration
    String notMentioned() {
      return "Hello!"
    }

    //This method is both configured to be traced and to be excluded. Should NOT be traced.
    String excluded() {
      return "Hello!"
    }

    //This method is annotated with annotation which usually results in a captured span
    @Trace
    String annotated() {
      return "Hello!"
    }

    //This method is annotated with annotation which usually results in a captured span, but is configured to be
    //excluded.
    @Trace
    String annotatedButExcluded() {
      return "Hello!"
    }
  }


  //Baseline and assumption validation
  def "Calling these methods should be traced"() {
    expect:
    new TestClass().included() == "Hello!"
    new TestClass().annotated() == "Hello!"

    and:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "TestClass.included"
          attributes {
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "TestClass.annotated"
          attributes {
          }
        }
      }
    }
  }

  def "Not explicitly configured method should not be traced"() {
    expect:
    new TestClass().notMentioned() == "Hello!"

    and:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

  def "Method which is both included and excluded for tracing should NOT be traced"() {
    expect:
    new TestClass().excluded() == "Hello!"

    and:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

  def "Method exclusion should override tracing annotations"() {
    expect:
    new TestClass().annotatedButExcluded() == "Hello!"

    and:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }
}
