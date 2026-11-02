/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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
import org.junit.jupiter.params.provider.EnumSource;
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
    assertThat(oldState.shouldProcessWrite()).isFalse();
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
    assertThat(fixture.requestState()).isSameAs(state);
    assertThat(fixture.stateAttributeUpdates).isEqualTo(1);
  }

  @Test
  void streamStateIsRegisteredOnlyAfterSuccessfulStreamAcquisition() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();

    InjectionState state = requireNonNull(fixture.requestState());
    assertThat(fixture.stateAttributeUpdates).isEqualTo(1);
    assertThat(INJECTION_STATE.get(fixture.stream)).isNull();

    fixture.failOutputStreamAcquisition = true;
    fixture.assertNoStreamStateBeforeOutputAcquisition = true;
    assertThatThrownBy(wrapper::getOutputStream).isInstanceOf(IOException.class);

    assertThat(fixture.requestState()).isSameAs(state);
    assertThat(fixture.stateAttributeUpdates).isEqualTo(1);
    assertThat(INJECTION_STATE.get(fixture.stream)).isNull();

    assertThat(wrapper.getOutputStream()).isSameAs(fixture.stream);
    fixture.assertNoStreamStateBeforeOutputAcquisition = false;
    assertThat(ServletOutputStreamInjectionState.getInjectionState(fixture.stream)).isSameAs(state);
    assertThat(fixture.stateAttributeUpdates).isEqualTo(1);
  }

  @Test
  void streamAcquisitionSharesStateAcrossWrappersForSameBody() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper first = fixture.wrap();
    ServletOutputStream stream = first.getOutputStream();
    InjectionStateReference firstReference = INJECTION_STATE.get(stream);

    Servlet5SnippetInjectingResponseWrapper second = fixture.wrap();
    assertThat(second.getOutputStream()).isSameAs(stream);
    InjectionState secondState = requireNonNull(fixture.requestState());

    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isSameAs(secondState);
    assertThat(INJECTION_STATE.get(stream)).isSameAs(firstReference);
    assertThat(secondState).isSameAs(first.getInjectionState());
    assertThat(secondState.getWrapper()).isSameAs(second);
    assertThat(fixture.stateAttributeUpdates).isEqualTo(1);
  }

  @Test
  void completedInjectionSkipsRequestLifetimeLookup() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    ServletOutputStream stream = wrapper.getOutputStream();
    fixture.requestAttributeGets = 0;

    stream.write("<head>".getBytes(UTF_8));
    InjectionState state = requireNonNull(fixture.requestState());
    assertThat(state.isHeadTagWritten()).isTrue();
    assertThat(fixture.requestAttributeGets).isEqualTo(2);

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

  @ParameterizedTest
  @EnumSource(Output.class)
  void redispatchRetainsPartialMatchAndInjectsOnce(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentType("text/html");
    first.setContentLength(12);
    fixture.write(first, output, "<he");

    Servlet5SnippetInjectingResponseWrapper dispatched = fixture.wrap();
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
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
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
    Servlet5SnippetInjectingResponseWrapper first = fixture.wrap();
    first.setContentLength(5);
    first.setContentType("text/plain");
    fixture.write(first, Output.STREAM, "Hello");
    fixture.response.reset();
    when(fixture.request.getDispatcherType()).thenReturn(DispatcherType.ERROR);

    Servlet5SnippetInjectingResponseWrapper error = fixture.wrap();
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
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    wrapper.setContentLength(1000);
    fixture.write(wrapper, output == Output.WRITER ? Output.STREAM : Output.WRITER, "<head>");
    wrapper.reset();
    wrapper.setContentType("text/html");
    fixture.write(wrapper, output, HTML);
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.contentLength).isEqualTo(-1);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void resetBufferDiscardsPartialMatchAcrossRedispatch(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    fixture.write(wrapper, output, "<he");
    wrapper.resetBuffer();
    fixture.write(fixture.wrap(), output, "ad><head>");
    assertThat(fixture.content()).isEqualTo("ad><head>" + SNIPPET);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void resetBufferDoesNotAdjustContentLengthTwice(Output output) throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    wrapper.setContentLength(HTML.length());
    fixture.write(wrapper, output, HTML);
    assertThat(fixture.contentLength).isEqualTo(INJECTED.length());

    wrapper.resetBuffer();
    assertThat(fixture.contentLength).isEqualTo(HTML.length());
    fixture.write(fixture.wrap(), output, HTML);
    assertThat(fixture.content()).isEqualTo(INJECTED);
    assertThat(fixture.contentLength).isEqualTo(INJECTED.length());
  }

  @Test
  void failedWriterAcquisitionDoesNotDisableStreamInjection() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    ServletOutputStream output = wrapper.getOutputStream();
    assertThatIllegalStateException().isThrownBy(wrapper::getWriter);
    output.write(HTML.getBytes(UTF_8));
    assertThat(fixture.content()).isEqualTo(INJECTED);
  }

  @Test
  void resetBufferRetainsWriterOwnership() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.setContentType("text/html");
    wrapper.getWriter();
    wrapper.resetBuffer();
    assertThatIllegalStateException().isThrownBy(wrapper::getOutputStream);
    assertThat(wrapper.getInjectionState().shouldProcessStreamWrite()).isFalse();
    fixture.write(wrapper, Output.WRITER, HTML);
    assertThat(fixture.content()).isEqualTo(INJECTED);
  }

  @Test
  void cachedWriterConsultsNativeOwnershipAfterRawReset() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    wrapper.getWriter();
    fixture.response.reset();
    fixture.response.getOutputStream();
    assertThatIllegalStateException().isThrownBy(wrapper::getWriter);
  }

  @Test
  void cachedWriterReplacedAfterRawResetAndEncodingChange() throws IOException {
    Fixture fixture = new Fixture();
    Servlet5SnippetInjectingResponseWrapper wrapper = fixture.wrap();
    PrintWriter old = wrapper.getWriter();
    fixture.response.reset();
    fixture.response.setCharacterEncoding("UTF-16LE");
    wrapper.setContentType("text/html");
    PrintWriter replacement = wrapper.getWriter();
    assertThat(replacement).isNotSameAs(old);
    replacement.write(HTML);
    replacement.flush();
    assertThat(new String(fixture.bytes.toByteArray(), java.nio.charset.UTF_16LE))
        .isEqualTo(INJECTED);
  }

  enum Output {
    STREAM,
    WRITER
  }

  private static class Fixture {
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final HttpServletResponse response = mock(HttpServletResponse.class);
    final Map<String, Object> attributes = new HashMap<>();
    int stateAttributeUpdates;
    int requestAttributeGets;
    boolean failOutputStreamAcquisition;
    boolean assertNoStreamStateBeforeOutputAcquisition;
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
    Output output;
    PrintWriter writer;
    String encoding = UTF_8.name();

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
                if (name.endsWith(".state")) {
                  stateAttributeUpdates++;
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
      when(response.getCharacterEncoding()).thenAnswer(invocation -> encoding);
      doAnswer(
              invocation -> {
                encoding = invocation.getArgument(0);
                return null;
              })
          .when(response)
          .setCharacterEncoding(anyString());
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
                if (assertNoStreamStateBeforeOutputAcquisition) {
                  assertThat(INJECTION_STATE.get(stream)).isNull();
                }
                if (failOutputStreamAcquisition) {
                  failOutputStreamAcquisition = false;
                  throw new IOException("stream acquisition failed");
                }
                if (output == Output.WRITER) {
                  throw new IllegalStateException("Writer already acquired");
                }
                output = Output.STREAM;
                return stream;
              });
      when(response.getWriter())
          .thenAnswer(
              invocation -> {
                if (output == Output.STREAM) {
                  throw new IllegalStateException("Stream already acquired");
                }
                output = Output.WRITER;
                if (writer == null) {
                  writer = new PrintWriter(new OutputStreamWriter(stream, encoding));
                }
                return writer;
              });
      doAnswer(
              invocation -> {
                bytes.reset();
                return null;
              })
          .when(response)
          .resetBuffer();
      doAnswer(
              invocation -> {
                bytes.reset();
                contentLength = -1;
                contentType = null;
                output = null;
                writer = null;
                encoding = UTF_8.name();
                return null;
              })
          .when(response)
          .reset();
    }

    Servlet5SnippetInjectingResponseWrapper wrap() {
      return new Servlet5SnippetInjectingResponseWrapper(request, response, SNIPPET);
    }

    void recycle() {
      attributes.clear();
      bytes.reset();
      contentType = null;
      contentLength = -1;
      output = null;
      writer = null;
      encoding = UTF_8.name();
    }

    void write(Servlet5SnippetInjectingResponseWrapper wrapper, Output output, String text)
        throws IOException {
      assertThat(wrapper.getResponse()).isSameAs(response);
      if (output == Output.STREAM) {
        wrapper.getOutputStream().write(text.getBytes(UTF_8));
      } else {
        wrapper.getWriter().write(text);
        wrapper.getWriter().flush();
      }
    }

    InjectionState requestState() {
      return (InjectionState)
          attributes.get(Servlet5SnippetInjectingResponseWrapper.class.getName() + ".state");
    }

    String content() {
      return new String(bytes.toByteArray(), UTF_8);
    }
  }
}
