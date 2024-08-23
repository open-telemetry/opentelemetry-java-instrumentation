/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpServletResponseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final TestResponse response = new TestResponse();
  private final HttpServletRequest request = mock(HttpServletRequest.class);

  @BeforeEach
  void setUp() throws ServletException, IOException {
    when(request.getMethod()).thenReturn("GET");
    when(request.getProtocol()).thenReturn("TEST");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());

    HttpServlet servlet = new HttpServlet() {};
    // We need to call service so HttpServletAdvice can link the request to the response.
    servlet.service(request, response);
    testing.clearData();
  }

  @Test
  void test_send_no_parent() throws IOException {
    response.sendError(0);
    response.sendError(0, "");
    response.sendRedirect("");

    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void test_send_with_parent() throws IOException {
    runWithSpan(
        "parent",
        () -> {
          response.sendError(0);
          response.sendError(0, "");
          response.sendRedirect("");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TestResponse.sendError")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TestResponse.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sendError")),
                span ->
                    span.hasName("TestResponse.sendError")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TestResponse.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sendError")),
                span ->
                    span.hasName("TestResponse.sendRedirect")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                TestResponse.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sendRedirect"))));
  }

  @Test
  void test_send_with_exception() throws ServletException, IOException {
    TestResponse response =
        new TestResponse() {
          @Override
          public void sendRedirect(String s) {
            throw new RuntimeException("some error");
          }
        };
    HttpServlet servlet = new HttpServlet() {};
    // We need to call service so HttpServletAdvice can link the request to the response.
    servlet.service(request, response);
    testing.clearData();

    assertThatCode(
            () ->
                runWithSpan(
                    "parent",
                    () -> {
                      try {
                        response.sendRedirect("");
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("some error");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(new RuntimeException("some error")),
                span ->
                    span.hasName("HttpServletResponseTest$2.sendRedirect")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new RuntimeException("some error"))));
  }

  /** Tests deprecated methods */
  public static class TestResponse implements HttpServletResponse {
    @Override
    public void addCookie(Cookie cookie) {}

    @Override
    public boolean containsHeader(String s) {
      return false;
    }

    @Override
    public String encodeURL(String s) {
      return null;
    }

    @Override
    public String encodeRedirectURL(String s) {
      return null;
    }

    // test deprecated methods
    @SuppressWarnings("deprecation")
    @Override
    public String encodeUrl(String s) {
      return null;
    }

    // test deprecated methods
    @SuppressWarnings("deprecation")
    @Override
    public String encodeRedirectUrl(String s) {
      return null;
    }

    @Override
    public void sendError(int i, String s) {}

    @Override
    public void sendError(int i) {}

    @Override
    public void sendRedirect(String s) throws IOException {}

    @Override
    public void setDateHeader(String s, long l) {}

    @Override
    public void addDateHeader(String s, long l) {}

    @Override
    public void setHeader(String s, String s1) {}

    @Override
    public void addHeader(String s, String s1) {}

    @Override
    public void setIntHeader(String s, int i) {}

    @Override
    public void addIntHeader(String s, int i) {}

    @Override
    public void setStatus(int i) {}

    // test deprecated methods
    @SuppressWarnings("deprecation")
    @Override
    public void setStatus(int i, String s) {}

    @Override
    public int getStatus() {
      return 0;
    }

    @Override
    public String getHeader(String s) {
      return null;
    }

    @SuppressWarnings("ReturnsNullCollection")
    @Override
    public Collection<String> getHeaders(String s) {
      return null;
    }

    @SuppressWarnings("ReturnsNullCollection")
    @Override
    public Collection<String> getHeaderNames() {
      return null;
    }

    @Override
    public String getCharacterEncoding() {
      return null;
    }

    @Override
    public String getContentType() {
      return null;
    }

    @Override
    public ServletOutputStream getOutputStream() {
      return null;
    }

    @Override
    public PrintWriter getWriter() {
      return null;
    }

    @Override
    public void setCharacterEncoding(String charset) {}

    @Override
    public void setContentLength(int i) {}

    @Override
    public void setContentLengthLong(long l) {}

    @Override
    public void setContentType(String s) {}

    @Override
    public void setBufferSize(int i) {}

    @Override
    public int getBufferSize() {
      return 0;
    }

    @Override
    public void flushBuffer() {}

    @Override
    public void resetBuffer() {}

    @Override
    public boolean isCommitted() {
      return false;
    }

    @Override
    public void reset() {}

    @Override
    public void setLocale(Locale locale) {}

    @Override
    public Locale getLocale() {
      return null;
    }
  }
}