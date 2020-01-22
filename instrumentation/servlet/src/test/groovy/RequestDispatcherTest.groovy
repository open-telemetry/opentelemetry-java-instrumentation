import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class RequestDispatcherTest extends AgentTestRunner {

  def dispatcher = new RequestDispatcherUtils(Mock(HttpServletRequest), Mock(HttpServletResponse))

  def "test dispatch no-parent"() {
    when:
    dispatcher.forward("")
    dispatcher.include("")

    then:
    assertTraces(0) {}
  }

  def "test dispatcher #method with parent"() {
    when:
    runUnderTrace("parent") {
      dispatcher."$method"(target)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "servlet.$operation"
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" target
            "$Tags.COMPONENT" "java-web-servlet-dispatcher"
          }
        }
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
    def dispatcher = new RequestDispatcherUtils(Mock(HttpServletRequest), Mock(HttpServletResponse), ex)

    when:
    runUnderTrace("parent") {
      dispatcher."$method"(target)
    }

    then:
    def th = thrown(ServletException)
    th == ex

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, null, ex)
        span(1) {
          operationName "servlet.$operation"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" target
            "$Tags.COMPONENT" "java-web-servlet-dispatcher"
            errorTags(ex.class, ex.message)
          }
        }
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
