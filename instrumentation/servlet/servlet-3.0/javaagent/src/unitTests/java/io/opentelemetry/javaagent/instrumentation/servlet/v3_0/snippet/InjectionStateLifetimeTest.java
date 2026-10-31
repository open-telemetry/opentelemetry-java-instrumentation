/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.DispatcherType;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class InjectionStateLifetimeTest {
  private static final String SNIPPET = "<script>console.log(hi)</script>";
  private static final String HTML = "<head><title>complete</title></head><body>complete</body>";
  private static final String INJECTED =
      "<head>" + SNIPPET + "<title>complete</title></head><body>complete</body>";

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reusedStreamDoesNotInjectOldStateIntoWriter(boolean fixedLength) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentType("text/plain");
    first.setContentLength(5);
    first.getOutputStream().write("Hello".getBytes(UTF_8));
    assertThat(fixture.content()).isEqualTo("Hello");
    InjectionState oldState = ServletOutputStreamInjectionState.getInjectionState(fixture.stream);

    fixture.recycle();
    Servlet3SnippetInjectingResponseWrapper second = fixture.wrap();
    PrintWriter writer = second.getWriter();
    assertThat(oldState.shouldProcessWrite()).isFalse();
    second.setContentType("text/html");
    if (fixedLength) {
      second.setContentLength(HTML.length());
    }
    writer.write(HTML);
    writer.flush();

    assertThat(ServletOutputStreamInjectionState.getInjectionState(fixture.stream)).isNull();
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.content()).containsOnlyOnce(SNIPPET);
    assertThat(fixture.contentLength).isEqualTo(fixedLength ? INJECTED.length() : -1);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void redispatchRetainsPartialMatchAndInjectsOnce(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentType("text/html");
    first.setContentLength(12);
    fixture.write(first, output, "<he");

    Servlet3SnippetInjectingResponseWrapper dispatched = fixture.wrap();
    assertThat(dispatched.getInjectionState()).isSameAs(first.getInjectionState());
    fixture.write(dispatched, output, "ad><head>");
    fixture.write(first, output, "<head>");

    assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET + "<head><head>");
    assertThat(fixture.contentLength).isEqualTo(12 + SNIPPET.length());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void streamStateSurvivesHandlerExitUntilContainerWrites(boolean recycled) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.getOutputStream();
    wrapper.setContentType("text/html");
    if (recycled) {
      fixture.recycle();
      fixture.contentType = "text/html";
    }
    // Async writes and container finalization can use the raw stream without reacquiring it.
    fixture.stream.write(HTML.getBytes(UTF_8));
    assertThat(fixture.content()).isEqualTo(recycled ? HTML : INJECTED);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void errorDispatchDoesNotInheritAbandonedContentLength(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentLength(5);
    first.setContentType("text/plain");
    fixture.write(first, Output.STREAM, "Hello");
    fixture.bytes.reset();
    fixture.contentLength = -1;
    fixture.contentType = null;
    when(fixture.request.getDispatcherType()).thenReturn(DispatcherType.ERROR);

    Servlet3SnippetInjectingResponseWrapper error = fixture.wrap();
    assertThat(error.getInjectionState()).isNotSameAs(first.getInjectionState());
    error.setContentType("text/html");
    fixture.write(error, output, HTML);
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.contentLength).isEqualTo(-1);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void resetDiscardsBodyStateAndAllowsNewOutputSelection(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    wrapper.setContentLength(1000);
    fixture.write(wrapper, output == Output.WRITER ? Output.STREAM : Output.WRITER, "<head>");
    wrapper.reset();
    fixture.bytes.reset();
    fixture.contentLength = -1;
    wrapper.setContentType("text/html");
    fixture.write(wrapper, output, HTML);
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.contentLength).isEqualTo(-1);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void resetBufferDiscardsPartialMatchAcrossRedispatch(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    fixture.write(wrapper, output, "<he");
    wrapper.resetBuffer();
    fixture.bytes.reset();
    fixture.write(fixture.wrap(), output, "ad><head>");
    assertThat(fixture.content()).isEqualTo("ad><head>" + SNIPPET);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void resetBufferDoesNotAdjustContentLengthTwice(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet3SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    wrapper.setContentLength(HTML.length());
    fixture.write(wrapper, output, HTML);
    assertThat(fixture.contentLength).isEqualTo(INJECTED.length());

    wrapper.resetBuffer();
    fixture.bytes.reset();
    assertThat(fixture.contentLength).isEqualTo(HTML.length());
    fixture.write(fixture.wrap(), output, HTML);
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.contentLength).isEqualTo(INJECTED.length());
  }

  enum Output {
    STREAM,
    WRITER
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
      when(response.getWriter()).thenReturn(new PrintWriter(new OutputStreamWriter(stream, UTF_8)));
    }

    Servlet3SnippetInjectingResponseWrapper wrap() {
      return new Servlet3SnippetInjectingResponseWrapper(request, response, SNIPPET);
    }

    void recycle() {
      attributes.clear();
      bytes.reset();
      contentType = null;
      contentLength = -1;
    }

    void write(Servlet3SnippetInjectingResponseWrapper wrapper, Output output, String text)
        throws IOException {
      assertThat(wrapper.getResponse()).isSameAs(response);
      if (output == Output.STREAM) {
        wrapper.getOutputStream().write(text.getBytes(UTF_8));
      } else {
        wrapper.getWriter().write(text);
        wrapper.getWriter().flush();
      }
    }

    String content() {
      return new String(bytes.toByteArray(), UTF_8);
    }
  }
}
