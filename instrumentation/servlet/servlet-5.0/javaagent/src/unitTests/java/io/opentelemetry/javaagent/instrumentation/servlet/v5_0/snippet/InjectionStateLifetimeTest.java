/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.OutputStreamSnippetInjectionHelper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InjectionStateLifetimeTest {
  private static final String SNIPPET = "<script>console.log(hi)</script>";
  private static final String HTML = "<head><title>complete</title></head><body>complete</body>";
  private static final String INJECTED =
      "<head>" + SNIPPET + "<title>complete</title></head><body>complete</body>";

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void recycledStreamDoesNotAffectNextRequestWriter(boolean fixedLength) throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentType("text/plain");
    first.setContentLength(5);
    first.getOutputStream().write("Hello".getBytes(UTF_8));
    InjectionState oldState = ServletOutputStreamInjectionState.getInjectionState(fixture.stream);

    fixture.recycle();
    Servlet5SnippetInjectingResponseWrapper second = fixture.wrap();
    second.setContentType("text/html");
    if (fixedLength) {
      second.setContentLength(HTML.length());
    }
    second.getWriter().write(HTML);
    second.getWriter().flush();

    assertThat(oldState.isCurrentRequest()).isFalse();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(fixture.stream)).isNull();
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.content()).containsOnlyOnce(SNIPPET);
    assertThat(fixture.contentLength).isEqualTo(fixedLength ? INJECTED.length() : -1);
  }

  @Test
  void acquiredStreamRemainsActiveAfterServiceAndDispatch() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    wrapper.getOutputStream();
    when(fixture.request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);

    fixture.stream.write(HTML.getBytes(UTF_8));

    assertThat(fixture.content()).isEqualTo(INJECTED);
  }

  private static class Fixture {
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final HttpServletResponse response = mock(HttpServletResponse.class);
    final Map<String, Object> attributes = new HashMap<>();
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final OutputStreamSnippetInjectionHelper helper =
        new OutputStreamSnippetInjectionHelper(() -> SNIPPET);
    final ServletOutputStream stream =
        new ServletOutputStream() {
          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(WriteListener listener) {}

          @Override
          public void write(int b) throws IOException {
            InjectionState state = ServletOutputStreamInjectionState.getInjectionState(this);
            if (state == null || !helper.handleWrite(state, this, b)) {
              bytes.write(b);
            }
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            InjectionState state = ServletOutputStreamInjectionState.getInjectionState(this);
            if (state == null || !helper.handleWrite(state, this, b, off, len)) {
              bytes.write(b, off, len);
            }
          }
        };
    String contentType;
    int contentLength = -1;

    Fixture() throws IOException {
      when(request.getAttribute(anyString()))
          .thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
      doAnswer(
              invocation -> {
                attributes.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
              })
          .when(request)
          .setAttribute(anyString(), any());
      doAnswer(
              invocation -> {
                attributes.remove(invocation.getArgument(0));
                return null;
              })
          .when(request)
          .removeAttribute(anyString());
      when(response.getContentType()).thenAnswer(invocation -> contentType);
      when(response.getCharacterEncoding()).thenReturn(UTF_8.name());
      doAnswer(
              invocation -> {
                contentType = invocation.getArgument(0);
                return null;
              })
          .when(response)
          .setContentType(anyString());
      doAnswer(
              invocation -> {
                contentLength = invocation.getArgument(0);
                return null;
              })
          .when(response)
          .setContentLength(anyInt());
      when(response.getOutputStream()).thenReturn(stream);
      when(response.getWriter())
          .thenAnswer(invocation -> new PrintWriter(new OutputStreamWriter(stream, UTF_8)));
    }

    Servlet5SnippetInjectingResponseWrapper wrap() {
      return new Servlet5SnippetInjectingResponseWrapper(request, response, SNIPPET);
    }

    void recycle() {
      attributes.clear();
      bytes.reset();
      contentType = null;
      contentLength = -1;
    }

    String content() {
      return new String(bytes.toByteArray(), UTF_8);
    }
  }
}
