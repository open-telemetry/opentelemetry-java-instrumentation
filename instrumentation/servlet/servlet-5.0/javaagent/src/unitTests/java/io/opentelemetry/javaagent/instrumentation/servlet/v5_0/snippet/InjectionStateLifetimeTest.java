/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateReference;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.OutputStreamSnippetInjectionHelper;
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
  private static final VirtualField<ServletOutputStream, InjectionStateReference> INJECTION_STATE =
      VirtualField.find(ServletOutputStream.class, InjectionStateReference.class);
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
  void recycledStreamDoesNotReusePreviousRequestMatchOrContentLength() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentType("text/html");
    first.setContentLength(5);
    first.getOutputStream().write("<he".getBytes(UTF_8));
    InjectionState oldState = ServletOutputStreamInjectionState.getInjectionState(fixture.stream);
    assertThat(oldState.getHeadTagBytesSeen()).isEqualTo(3);

    fixture.recycle();
    Servlet5SnippetInjectingResponseWrapper second = fixture.wrap();
    second.setContentType("text/html");
    second.setContentLength("ad><head>".length());
    assertThat(second.getOutputStream()).isSameAs(fixture.stream);
    fixture.stream.write("ad><head>".getBytes(UTF_8));
    fixture.stream.flush();

    assertThat(oldState.isCurrentRequest()).isFalse();
    assertThat(fixture.content()).isEqualTo("ad><head>" + SNIPPET);
    assertThat(fixture.content()).containsOnlyOnce(SNIPPET);
    assertThat(fixture.contentLength).isEqualTo("ad><head>".length() + SNIPPET.length());
  }

  @Test
  void repeatedStreamAcquisitionKeepsRequestAndStreamState() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    ServletOutputStream stream = wrapper.getOutputStream();
    InjectionState state = ServletOutputStreamInjectionState.getInjectionState(stream);
    InjectionStateReference reference = INJECTION_STATE.get(stream);

    assertThat(wrapper.getOutputStream()).isSameAs(stream);
    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isSameAs(state);
    assertThat(INJECTION_STATE.get(stream)).isSameAs(reference);
    assertThat(fixture.requestStreamState()).isSameAs(state);
    assertThat(fixture.streamStateAttributeUpdates).isEqualTo(1);
  }

  @Test
  void streamStateIsCreatedOnlyAfterSuccessfulStreamAcquisition() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();

    assertThat(fixture.requestStreamState()).isNull();
    assertThat(fixture.streamStateAttributeUpdates).isZero();
    assertThat(INJECTION_STATE.get(fixture.stream)).isNull();

    fixture.failOutputStreamAcquisition = true;
    fixture.assertNoStateBeforeOutputAcquisition = true;
    assertThatThrownBy(wrapper::getOutputStream).isInstanceOf(IOException.class);

    assertThat(fixture.requestStreamState()).isNull();
    assertThat(fixture.streamStateAttributeUpdates).isZero();
    assertThat(INJECTION_STATE.get(fixture.stream)).isNull();

    assertThat(wrapper.getOutputStream()).isSameAs(fixture.stream);
    fixture.assertNoStateBeforeOutputAcquisition = false;
    assertThat(fixture.requestStreamState()).isNotNull();
    assertThat(fixture.streamStateAttributeUpdates).isEqualTo(1);
  }

  @Test
  void streamAcquisitionReplacesStateForChangedWrapper() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper first = fixture.wrap();
    ServletOutputStream stream = first.getOutputStream();
    InjectionStateReference firstReference = INJECTION_STATE.get(stream);

    Servlet5SnippetInjectingResponseWrapper second = fixture.wrap();
    assertThat(second.getOutputStream()).isSameAs(stream);
    InjectionState secondState = requireNonNull(fixture.requestStreamState());

    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isSameAs(secondState);
    assertThat(INJECTION_STATE.get(stream)).isNotSameAs(firstReference);
    assertThat(fixture.requestStreamState()).isSameAs(secondState);
    assertThat(fixture.streamStateAttributeUpdates).isEqualTo(2);
  }

  @Test
  void completedInjectionSkipsRequestLifetimeLookup() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    ServletOutputStream stream = wrapper.getOutputStream();
    fixture.requestAttributeGets = 0;

    stream.write("<head>".getBytes(UTF_8));
    InjectionState state = requireNonNull(fixture.requestStreamState());
    assertThat(state.isHeadTagWritten()).isTrue();
    assertThat(fixture.requestAttributeGets).isEqualTo(1);

    fixture.attributes.clear();
    fixture.requestAttributeGets = 0;

    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isSameAs(state);
    stream.write("tail".getBytes(UTF_8));

    assertThat(fixture.requestAttributeGets).isZero();
    assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET + "tail");
    assertThat(fixture.content()).containsOnlyOnce(SNIPPET);
  }

  @Test
  void unfinishedInjectionStateIsRejectedAfterRequestExpires() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    ServletOutputStream stream = wrapper.getOutputStream();
    stream.write("<he".getBytes(UTF_8));

    fixture.attributes.clear();

    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isNull();
    assertThat(INJECTION_STATE.get(stream)).isNull();
  }

  private static class Fixture {
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final HttpServletResponse response = mock(HttpServletResponse.class);
    final Map<String, Object> attributes = new HashMap<>();
    int streamStateAttributeUpdates;
    int requestAttributeGets;
    boolean failOutputStreamAcquisition;
    boolean assertNoStateBeforeOutputAcquisition;
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
          .thenAnswer(
              invocation -> {
                requestAttributeGets++;
                return attributes.get(invocation.getArgument(0));
              });
      doAnswer(
              invocation -> {
                String name = invocation.getArgument(0);
                attributes.put(name, invocation.getArgument(1));
                if (name.endsWith(".streamState")) {
                  streamStateAttributeUpdates++;
                }
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
      when(response.getOutputStream())
          .thenAnswer(
              invocation -> {
                if (assertNoStateBeforeOutputAcquisition) {
                  assertThat(streamStateAttributeUpdates).isZero();
                }
                if (failOutputStreamAcquisition) {
                  failOutputStreamAcquisition = false;
                  throw new IOException("stream acquisition failed");
                }
                return stream;
              });
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

    InjectionState requestStreamState() {
      return (InjectionState)
          attributes.get(Servlet5SnippetInjectingResponseWrapper.class.getName() + ".streamState");
    }

    String content() {
      return new String(bytes.toByteArray(), UTF_8);
    }
  }
}
