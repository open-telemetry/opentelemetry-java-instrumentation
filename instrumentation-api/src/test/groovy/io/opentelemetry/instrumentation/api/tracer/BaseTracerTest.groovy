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

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.trace.Span
import spock.lang.Shared

// TODO add tests for BaseTracer
class BaseTracerTest extends AgentSpecification {
  @Shared
  def tracer = newTracer()

  @Shared
  def resolvedAddress = new InetSocketAddress("github.com", 999)

  def span = Mock(Span)

  def newTracer() {
    return new BaseTracer() {
      @Override
      protected String getInstrumentationName() {
        return "BaseTracerTest"
      }
    }
  }

  class SomeInnerClass implements Runnable {
    void run() {
    }
  }

  static class SomeNestedClass implements Runnable {
    void run() {
    }
  }
}
	