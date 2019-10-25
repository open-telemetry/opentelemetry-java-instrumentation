import datadog.trace.agent.test.AgentTestRunner
import groovy.servlet.AbstractHttpServlet
import javax.servlet.ServletOutputStream
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import spock.lang.Subject

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class HttpServletResponseTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.servlet.enabled", "true")
  }

  @Subject
  def response = new TestResponse()
  def request = Mock(HttpServletRequest) {
    getMethod() >> "GET"
    getProtocol() >> "TEST"
  }

  def setup() {
    def servlet = new AbstractHttpServlet() {}
    // We need to call service so HttpServletAdvice can link the request to the response.
    servlet.service(request, response)
  }

  def "test send no-parent"() {
    when:
    response.sendError(0)
    response.sendError(0, "")
    response.sendRedirect("")

    then:
    assertTraces(0) {}
  }

  def "test send with parent"() {
    when:
    runUnderTrace("parent") {
      response.sendError(0)
      response.sendError(0, "")
      response.sendRedirect("")
    }

    then:
    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "servlet.response"
          resourceName "HttpServletResponse.sendRedirect"
          childOf span(0)
          tags {
            "component" "java-web-servlet-response"
            defaultTags()
          }
        }
        span(2) {
          operationName "servlet.response"
          resourceName "HttpServletResponse.sendError"
          childOf span(0)
          tags {
            "component" "java-web-servlet-response"
            defaultTags()
          }
        }
        span(3) {
          operationName "servlet.response"
          resourceName "HttpServletResponse.sendError"
          childOf span(0)
          tags {
            "component" "java-web-servlet-response"
            defaultTags()
          }
        }
      }
    }
  }

  def "test send with exception"() {
    setup:
    def ex = new Exception("some error")
    def response = new TestResponse() {
      @Override
      void sendRedirect(String s) {
        throw ex
      }
    }
    def servlet = new AbstractHttpServlet() {}
    // We need to call service so HttpServletAdvice can link the request to the response.
    servlet.service(request, response)

    when:
    runUnderTrace("parent") {
      response.sendRedirect("")
    }

    then:
    def th = thrown(Exception)
    th == ex

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, ex)
        span(1) {
          operationName "servlet.response"
          resourceName "HttpServletResponse.sendRedirect"
          childOf span(0)
          errored true
          tags {
            "component" "java-web-servlet-response"
            defaultTags()
            errorTags(ex.class, ex.message)
          }
        }
      }
    }
  }

  static class TestResponse implements HttpServletResponse {

    @Override
    void addCookie(Cookie cookie) {

    }

    @Override
    boolean containsHeader(String s) {
      return false
    }

    @Override
    String encodeURL(String s) {
      return null
    }

    @Override
    String encodeRedirectURL(String s) {
      return null
    }

    @Override
    String encodeUrl(String s) {
      return null
    }

    @Override
    String encodeRedirectUrl(String s) {
      return null
    }

    @Override
    void sendError(int i, String s) throws IOException {

    }

    @Override
    void sendError(int i) throws IOException {

    }

    @Override
    void sendRedirect(String s) throws IOException {

    }

    @Override
    void setDateHeader(String s, long l) {

    }

    @Override
    void addDateHeader(String s, long l) {

    }

    @Override
    void setHeader(String s, String s1) {

    }

    @Override
    void addHeader(String s, String s1) {

    }

    @Override
    void setIntHeader(String s, int i) {

    }

    @Override
    void addIntHeader(String s, int i) {

    }

    @Override
    void setStatus(int i) {

    }

    @Override
    void setStatus(int i, String s) {

    }

    @Override
    String getCharacterEncoding() {
      return null
    }

    @Override
    ServletOutputStream getOutputStream() throws IOException {
      return null
    }

    @Override
    PrintWriter getWriter() throws IOException {
      return null
    }

    @Override
    void setContentLength(int i) {

    }

    @Override
    void setContentType(String s) {

    }

    @Override
    void setBufferSize(int i) {

    }

    @Override
    int getBufferSize() {
      return 0
    }

    @Override
    void flushBuffer() throws IOException {

    }

    @Override
    void resetBuffer() {

    }

    @Override
    boolean isCommitted() {
      return false
    }

    @Override
    void reset() {

    }

    @Override
    void setLocale(Locale locale) {

    }

    @Override
    Locale getLocale() {
      return null
    }
  }
}
