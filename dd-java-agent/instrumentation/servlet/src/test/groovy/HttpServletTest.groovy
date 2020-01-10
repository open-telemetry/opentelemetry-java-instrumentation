import datadog.trace.agent.test.AgentTestRunner
import groovy.servlet.AbstractHttpServlet

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class HttpServletTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.servlet-service.enabled", "true")
  }

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
          operationName "servlet.service"
          resourceName "HttpServlet.service"
          childOf span(0)
          tags {
            "component" "java-web-servlet-service"
            defaultTags()
          }
        }
        span(2) {
          operationName "servlet.doGet"
          resourceName "${expectedResourceName}.doGet"
          childOf span(1)
          tags {
            "component" "java-web-servlet-service"
            defaultTags()
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

    expectedResourceName = servlet.class.anonymousClass ? servlet.class.name : servlet.class.simpleName
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
          operationName "servlet.service"
          resourceName "HttpServlet.service"
          childOf span(0)
          errored true
          tags {
            "component" "java-web-servlet-service"
            defaultTags()
            errorTags(ex.class, ex.message)
          }
        }
        span(2) {
          operationName "servlet.doGet"
          resourceName "${servlet.class.name}.doGet"
          childOf span(1)
          errored true
          tags {
            "component" "java-web-servlet-service"
            defaultTags()
            errorTags(ex.class, ex.message)
          }
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
