/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RequestDispatcherTest extends AgentInstrumentationSpecification {

  def request = Mock(HttpServletRequest)
  def response = Mock(HttpServletResponse)
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
          name "TestDispatcher.$operation"
          childOf span(0)
        }
        basicSpan(it, 2, "$operation-child", span(1))
      }
    }

    where:
    operation | method
    "forward" | "forward"
    "forward" | "forwardNamed"
    "include" | "include"
    "include" | "includeNamed"

    target = "test-$method"
  }

  def "test dispatcher #method with parent from request attribute"() {
    setup:
    Context context
    runUnderTrace("parent") {
      context = Context.current()
    }

    when:
    runUnderTrace("notParent") {
      context.makeCurrent().withCloseable {
        dispatcher."$method"(target)
      }
    }

    then:
    assertTraces(2) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "TestDispatcher.$operation"
          childOf span(0)
        }
        basicSpan(it, 2, "$operation-child", span(1))
      }
      trace(1, 1) {
        basicSpan(it, 0, "notParent")
      }
    }

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
          name "TestDispatcher.$operation"
          childOf span(0)
          errored true
          errorEvent(ex.class, ex.message)
        }
        basicSpan(it, 2, "$operation-child", span(1))
      }
    }

    where:
    operation | method
    "forward" | "forward"
    "forward" | "forwardNamed"
    "include" | "include"
    "include" | "includeNamed"

    target = "test-$method"
  }
}
