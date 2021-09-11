/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.servlet.AbstractHttpServlet
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.lang.Subject

import javax.servlet.ServletOutputStream
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.api.trace.StatusCode.ERROR
import static java.util.Collections.emptyEnumeration

class HttpServletResponseTest extends AgentInstrumentationSpecification {

  @Subject
  def response = new TestResponse()
  def request = Mock(HttpServletRequest) {
    getMethod() >> "GET"
    getProtocol() >> "TEST"
    getHeaderNames() >> emptyEnumeration()
    getAttributeNames() >> emptyEnumeration()
  }

  def setup() {
    def servlet = new AbstractHttpServlet() {}
    // We need to call service so HttpServletAdvice can link the request to the response.
    servlet.service((ServletRequest) request, (ServletResponse) response)
    clearExportedData()
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
    runWithSpan("parent") {
      response.sendError(0)
      response.sendError(0, "")
      response.sendRedirect("")
    }

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "TestResponse.sendError"
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "TestResponse.sendError"
          childOf span(0)
          attributes {
          }
        }
        span(3) {
          name "TestResponse.sendRedirect"
          childOf span(0)
          attributes {
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
    servlet.service((ServletRequest) request, (ServletResponse) response)
    clearExportedData()

    when:
    runWithSpan("parent") {
      response.sendRedirect("")
    }

    then:
    def th = thrown(Exception)
    th == ex

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(ex.class, ex.message)
        }
        span(1) {
          name 'HttpServletResponseTest$2.sendRedirect'
          childOf span(0)
          status ERROR
          errorEvent(ex.class, ex.message)
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
    String getContentType() {
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
    void setCharacterEncoding(String charset) {

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
