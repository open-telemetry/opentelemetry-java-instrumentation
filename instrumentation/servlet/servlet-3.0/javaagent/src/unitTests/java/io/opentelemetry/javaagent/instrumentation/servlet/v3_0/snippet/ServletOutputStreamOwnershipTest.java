/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateStore;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.OutputStreamSnippetInjectionHelper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class ServletOutputStreamOwnershipTest {
  private static final String SNIPPET = "<script>test</script>";

  @Test
  void requestKeepsStateForRawAsyncStream() throws Exception {
    HttpServletRequest request = request();
    TestStream stream = new TestStream();
    initializeAndWritePrefix(request, stream);

    GcUtils.awaitGc(Duration.ofSeconds(10));

    InjectionState state = ServletOutputStreamInjectionState.getInjectionState(stream);
    assertThat(state).isNotNull();
    assertThat(state.getHeadTagBytesSeen()).isEqualTo(3);
    write(stream, "ad><head>");
    assertThat(stream.bytes.toString(UTF_8.name())).isEqualTo("<head>" + SNIPPET + "<head>");
    assertThat(request.getAttribute(InjectionStateStore.REQUEST_ATTRIBUTE)).isNotNull();
  }

  @Test
  void preservesStreamAndWrapperIdentity() throws Exception {
    HttpServletRequest request = request();
    TestStream first = new TestStream();
    TestStream second = new TestStream();
    HttpServletResponse response = response(first);
    Servlet3SnippetInjectingResponseWrapper wrapper =
        new Servlet3SnippetInjectingResponseWrapper(request, response, SNIPPET);
    assertThat(wrapper.getOutputStream()).isSameAs(first);
    InjectionState firstState = ServletOutputStreamInjectionState.getInjectionState(first);
    wrapper.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first)).isSameAs(firstState);
    when(response.getOutputStream()).thenReturn(second);
    wrapper.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(second)).isNotSameAs(firstState);
    when(response.getOutputStream()).thenReturn(first);
    wrapper.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first)).isSameAs(firstState);

    Servlet3SnippetInjectingResponseWrapper other =
        new Servlet3SnippetInjectingResponseWrapper(request, response, SNIPPET);
    other.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first)).isNotSameAs(firstState);
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first).getWrapper())
        .isSameAs(other);
  }

  @Test
  void fallbackDoesNotRetainStreamOrResponseAfterRequest() throws Exception {
    WeakReference<?>[] references = createUnreachableCycle();
    GcUtils.awaitGc(references[0], Duration.ofSeconds(10));
    GcUtils.awaitGc(references[1], Duration.ofSeconds(10));
  }

  @Test
  void retainedStreamDoesNotOwnStateAfterRequest() throws Exception {
    HttpServletRequest request = request();
    TestStream stream = new TestStream();
    WeakReference<InjectionState> state = initializeState(request, stream);
    request.setAttribute(InjectionStateStore.REQUEST_ATTRIBUTE, null);
    clearInvocations(request);

    GcUtils.awaitGc(state, Duration.ofSeconds(10));
    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isNull();
  }

  private static WeakReference<InjectionState> initializeState(
      HttpServletRequest request, TestStream stream) throws IOException {
    new Servlet3SnippetInjectingResponseWrapper(request, response(stream), SNIPPET)
        .getOutputStream();
    return new WeakReference<>(ServletOutputStreamInjectionState.getInjectionState(stream));
  }

  private static WeakReference<?>[] createUnreachableCycle() throws IOException {
    HttpServletRequest request = request();
    TestStream stream = new TestStream();
    HttpServletResponse response = response(stream);
    new Servlet3SnippetInjectingResponseWrapper(request, response, SNIPPET).getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isNotNull();
    return new WeakReference<?>[] {new WeakReference<>(stream), new WeakReference<>(response)};
  }

  private static void initializeAndWritePrefix(HttpServletRequest request, TestStream stream)
      throws IOException {
    HttpServletResponse response = response(stream);
    new Servlet3SnippetInjectingResponseWrapper(request, response, SNIPPET).getOutputStream();
    write(stream, "<he");
  }

  private static void write(TestStream stream, String value) throws IOException {
    byte[] bytes = value.getBytes(UTF_8);
    InjectionState state = ServletOutputStreamInjectionState.getInjectionState(stream);
    if (!new OutputStreamSnippetInjectionHelper(() -> SNIPPET)
        .handleWrite(state, stream, bytes, 0, bytes.length)) {
      stream.write(bytes);
    }
  }

  private static HttpServletRequest request() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    AtomicReference<Object> attribute = new AtomicReference<>();
    when(request.getAttribute(anyString())).thenAnswer(invocation -> attribute.get());
    doAnswer(
            invocation -> {
              attribute.set(invocation.getArgument(1));
              return null;
            })
        .when(request)
        .setAttribute(eq(InjectionStateStore.REQUEST_ATTRIBUTE), org.mockito.any());
    return request;
  }

  private static HttpServletResponse response(TestStream stream) throws IOException {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getOutputStream()).thenReturn(stream);
    when(response.getContentType()).thenReturn("text/html");
    when(response.getCharacterEncoding()).thenReturn(UTF_8.name());
    return response;
  }

  private static class TestStream extends ServletOutputStream {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    @Override
    public void write(int value) {
      bytes.write(value);
    }
  }
}
