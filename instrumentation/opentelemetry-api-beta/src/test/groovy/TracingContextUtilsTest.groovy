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

import application.io.grpc.Context
import application.io.opentelemetry.OpenTelemetry
import io.opentelemetry.auto.test.AgentTestRunner

import static application.io.opentelemetry.trace.TracingContextUtils.currentContextWith
import static application.io.opentelemetry.trace.TracingContextUtils.getCurrentSpan
import static application.io.opentelemetry.trace.TracingContextUtils.getSpan
import static application.io.opentelemetry.trace.TracingContextUtils.getSpanWithoutDefault

class TracingContextUtilsTest extends AgentTestRunner {

  def "getCurrentSpan should return invalid"() {
    when:
    def span = getCurrentSpan()

    then:
    !span.context.valid
  }

  def "getCurrentSpan should return span"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = currentContextWith(testSpan)
    def span = getCurrentSpan()
    scope.close()

    then:
    span == testSpan
  }

  def "getSpan should return invalid"() {
    when:
    def span = getSpan(Context.current())

    then:
    !span.context.valid
  }

  def "getSpan should return span"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = currentContextWith(testSpan)
    def span = getSpan(Context.current())
    scope.close()

    then:
    span == testSpan
  }

  def "getSpanWithoutDefault should return null"() {
    when:
    def span = getSpanWithoutDefault(Context.current())

    then:
    span == null
  }

  def "getSpanWithoutDefault should return span"() {
    when:
    def tracer = OpenTelemetry.getTracer("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def scope = currentContextWith(testSpan)
    def span = getSpanWithoutDefault(Context.current())
    scope.close()

    then:
    span == testSpan
  }
}
