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
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.Span

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class RequestDispatcherTest extends AgentTestRunner {

  def request = Mock(HttpServletRequest)
  def response = Mock(HttpServletResponse)
  def mockSpan = Mock(Span)
  def dispatcher = new RequestDispatcherUtils(request, response)

  def "test dispatch no-parent"() {
    when:
    dispatcher.forward("")
    dispatcher.include("")

    then:
    assertTraces(2) {
      trace(0, 1) {
        basicSpan(it, 0, "forward-child")
      }
      trace(1, 1) {
        basicSpan(it, 0, "include-child")
      }
    }

    and:
    0 * _
  }

  def "test dispatcher #method with parent"() {
    when:
    runUnderTrace("parent") {
      dispatcher."$method"(target)
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "servlet.$operation"
          childOf span(0)
          tags {
            "dispatcher.target" target
            "$Tags.COMPONENT" "java-web-servlet-dispatcher"
          }
        }
        basicSpan(it, 2, "$operation-child", span(1))
      }
    }

    then:
    1 * request.getAttribute(SPAN_ATTRIBUTE) >> mockSpan
    then:
    1 * request.setAttribute(SPAN_ATTRIBUTE, { it.name == "servlet.$operation" })
    then:
    1 * request.setAttribute(SPAN_ATTRIBUTE, mockSpan)
    0 * _

    where:
    operation | method
    "forward" | "forward"
    "forward" | "forwardNamed"
    "include" | "include"
    "include" | "includeNamed"

    target = "test-$method"
  }

  def "test dispatcher #method exception"() {
    setup:
    def ex = new ServletException("some error")
    def dispatcher = new RequestDispatcherUtils(request, response, ex)

    when:
    runUnderTrace("parent") {
      dispatcher."$method"(target)
    }

    then:
    def th = thrown(ServletException)
    th == ex

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent", null, ex)
        span(1) {
          operationName "servlet.$operation"
          childOf span(0)
          errored true
          tags {
            "dispatcher.target" target
            "$Tags.COMPONENT" "java-web-servlet-dispatcher"
            errorTags(ex.class, ex.message)
          }
        }
        basicSpan(it, 2, "$operation-child", span(1))
      }
    }

    then:
    1 * request.getAttribute(SPAN_ATTRIBUTE) >> mockSpan
    then:
    1 * request.setAttribute(SPAN_ATTRIBUTE, { it.name == "servlet.$operation" })
    then:
    1 * request.setAttribute(SPAN_ATTRIBUTE, mockSpan)
    0 * _

    where:
    operation | method
    "forward" | "forward"
    "forward" | "forwardNamed"
    "include" | "include"
    "include" | "includeNamed"

    target = "test-$method"
  }
}
