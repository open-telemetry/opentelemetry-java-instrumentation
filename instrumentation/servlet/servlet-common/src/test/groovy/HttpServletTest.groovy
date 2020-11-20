/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import groovy.servlet.AbstractHttpServlet
import io.opentelemetry.instrumentation.test.AgentTestRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpServletTest extends AgentTestRunner {

  def req = Mock(HttpServletRequest) {
    getMethod() >> "GET"
    getProtocol() >> "TEST"
  }
  def resp = Mock(HttpServletResponse)

  def "test service no-parent"() {
    when:
    servlet.service(req, resp)

    then:
    assertTraces(0) {}

    where:
    servlet = new TestServlet()
  }

  def "test service with parent"() {
    when:
    runUnderTrace("parent") {
      servlet.service(req, resp)
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "HttpServlet.service"
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "${expectedSpanName}.doGet"
          childOf span(1)
          attributes {
          }
        }
      }
    }

    where:
    servlet << [new TestServlet(), new TestServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      }
    }]

    expectedSpanName = servlet.class.anonymousClass ? servlet.class.name : servlet.class.simpleName
  }

  def "test service exception"() {
    setup:
    def ex = new Exception("some error")
    def servlet = new TestServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        throw ex
      }
    }

    when:
    runUnderTrace("parent") {
      servlet.service(req, resp)
    }

    then:
    def th = thrown(Exception)
    th == ex

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent", null, ex)
        span(1) {
          name "HttpServlet.service"
          childOf span(0)
          errored true
          errorEvent(ex.class, ex.message)
        }
        span(2) {
          name "${servlet.class.name}.doGet"
          childOf span(1)
          errored true
          errorEvent(ex.class, ex.message)
        }
      }
    }
  }

  static class TestServlet extends AbstractHttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    }
  }
}
