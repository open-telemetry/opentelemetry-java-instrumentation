/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.InjectionStateStore;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.OutputStreamSnippetInjectionHelper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServletOutputStreamOwnershipTest {
  private static final String SNIPPET = "<script>test</script>";

  @Test
  void requestKeepsStateForRawAsyncStream()
      throws IOException, InterruptedException, TimeoutException {
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
    AtomicReference<TestStream> currentStream = new AtomicReference<>(first);
    HttpServletResponse response = response(currentStream);
    Servlet5SnippetInjectingResponseWrapper wrapper =
        new Servlet5SnippetInjectingResponseWrapper(request, response, SNIPPET);
    assertThat(wrapper.getOutputStream()).isSameAs(first);
    InjectionState firstState = ServletOutputStreamInjectionState.getInjectionState(first);
    wrapper.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first)).isSameAs(firstState);
    currentStream.set(second);
    wrapper.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(second)).isNotSameAs(firstState);
    currentStream.set(first);
    wrapper.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first)).isSameAs(firstState);

    Servlet5SnippetInjectingResponseWrapper other =
        new Servlet5SnippetInjectingResponseWrapper(request, response, SNIPPET);
    other.getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first)).isNotSameAs(firstState);
    assertThat(ServletOutputStreamInjectionState.getInjectionState(first).getWrapper())
        .isSameAs(other);
  }

  @Test
  void fallbackDoesNotRetainStreamOrResponseAfterRequest()
      throws IOException, InterruptedException, TimeoutException {
    WeakReference<?>[] references = createUnreachableCycle();
    GcUtils.awaitGc(references[0], Duration.ofSeconds(10));
    GcUtils.awaitGc(references[1], Duration.ofSeconds(10));
  }

  @Test
  void retainedStreamDoesNotOwnStateAfterRequest()
      throws IOException, InterruptedException, TimeoutException {
    HttpServletRequest request = request();
    TestStream stream = new TestStream();
    WeakReference<InjectionState> state = initializeState(request, stream);
    request.setAttribute(InjectionStateStore.REQUEST_ATTRIBUTE, null);

    GcUtils.awaitGc(state, Duration.ofSeconds(10));
    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isNull();
  }

  private static WeakReference<InjectionState> initializeState(
      HttpServletRequest request, TestStream stream) throws IOException {
    new Servlet5SnippetInjectingResponseWrapper(request, response(stream), SNIPPET)
        .getOutputStream();
    return new WeakReference<>(ServletOutputStreamInjectionState.getInjectionState(stream));
  }

  private static WeakReference<?>[] createUnreachableCycle() throws IOException {
    HttpServletRequest request = request();
    TestStream stream = new TestStream();
    HttpServletResponse response = response(stream);
    new Servlet5SnippetInjectingResponseWrapper(request, response, SNIPPET).getOutputStream();
    assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isNotNull();
    return new WeakReference<?>[] {new WeakReference<>(stream), new WeakReference<>(response)};
  }

  private static void initializeAndWritePrefix(HttpServletRequest request, TestStream stream)
      throws IOException {
    HttpServletResponse response = response(stream);
    new Servlet5SnippetInjectingResponseWrapper(request, response, SNIPPET).getOutputStream();
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
    AtomicReference<Object> attribute = new AtomicReference<>();
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getAttribute")) {
                return attribute.get();
              }
              if (method.getName().equals("setAttribute")) {
                attribute.set(args[1]);
              }
              return null;
            });
  }

  private static HttpServletResponse response(TestStream stream) {
    return response(new AtomicReference<>(stream));
  }

  private static HttpServletResponse response(AtomicReference<TestStream> stream) {
    return (HttpServletResponse)
        Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(),
            new Class<?>[] {HttpServletResponse.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getOutputStream":
                  return stream.get();
                case "getContentType":
                  return "text/html";
                case "getCharacterEncoding":
                  return UTF_8.name();
                default:
                  return null;
              }
            });
  }

  private static class TestStream extends ServletOutputStream {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    @Override
    public void write(int value) {
      bytes.write(value);
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener listener) {}
  }
}
