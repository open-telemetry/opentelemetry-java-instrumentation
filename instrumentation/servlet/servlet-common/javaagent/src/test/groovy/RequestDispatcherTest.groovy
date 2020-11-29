/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.api.trace.Span
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RequestDispatcherTest extends AgentTestRunner {

  def request = Mock(HttpServletRequest)
  def response = Mock(HttpServletResponse)
  def mockContext = Context.root().with(Mock(Span))
  def dispatcher = new RequestDispatcherUtils(request, response)

  def "test dispatch no-parent"() {
    when:
    dispatcher.forward("")
    dispatcher.include("")

    then:
    2 * request.getAttribute(CONTEXT_ATTRIBUTE)
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
    1 * request.getAttribute(CONTEXT_ATTRIBUTE)
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

    then:
    1 * request.getAttribute(CONTEXT_ATTRIBUTE) >> mockContext
    then:
    1 * request.setAttribute(CONTEXT_ATTRIBUTE, { Span.fromContext(it).name == "TestDispatcher.$operation" })
    then:
    1 * request.setAttribute(CONTEXT_ATTRIBUTE, mockContext)
    0 * _

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
    def mockContext = null
    runUnderTrace("parent") {
      mockContext = Context.current()
    }

    when:
    runUnderTrace("notParent") {
      dispatcher."$method"(target)
    }

    then:
    1 * request.getAttribute(CONTEXT_ATTRIBUTE) >> mockContext
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

    then:
    1 * request.getAttribute(CONTEXT_ATTRIBUTE) >> mockContext
    1 * request.setAttribute(CONTEXT_ATTRIBUTE, { Span.fromContext(it).name == "TestDispatcher.$operation" })
    1 * request.setAttribute(CONTEXT_ATTRIBUTE, { Span.fromContext(it).name == "parent" })
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

    1 * request.getAttribute(CONTEXT_ATTRIBUTE)
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

    then:
    1 * request.getAttribute(CONTEXT_ATTRIBUTE) >> mockContext
    then:
    1 * request.setAttribute(CONTEXT_ATTRIBUTE, { Span.fromContext(it).name == "TestDispatcher.$operation" })
    then:
    1 * request.setAttribute(CONTEXT_ATTRIBUTE, mockContext)
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
