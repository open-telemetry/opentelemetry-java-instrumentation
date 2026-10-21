/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0;

import static io.opentelemetry.api.common.AttributeKey.valueKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.common.ValueType;
import io.opentelemetry.instrumentation.servlet.v5_0.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.servlet.Filter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServletBodyCaptureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // TODO: to be replaced with 1.45.0 semconv incubating attribute when available
  private static final AttributeKey<Value<?>> BODY_ATTRIBUTE_KEY =
      valueKey("http.request.body.content");

  @Test
  void capturesRequestBody_default() throws Exception {
    ServletTelemetryBuilder builder = ServletTelemetry.builder(testing.getOpenTelemetry());
    testCaptureRequestBody(builder, null);
  }

  @Test
  void capturesRequestBody_enabled() throws Exception {
    ServletTelemetryBuilder builder = ServletTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setCaptureRequestBodyMaxSize(builder, 32);
    testCaptureRequestBody(builder, "request-body");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void capturesRequestBody_disabled(int maxSize) throws Exception {
    ServletTelemetryBuilder builder = ServletTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setCaptureRequestBodyMaxSize(builder, maxSize);
    testCaptureRequestBody(builder, null);
  }

  @Test
  void capturesRequestBody_truncated() throws Exception {
    ServletTelemetryBuilder builder = ServletTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setCaptureRequestBodyMaxSize(builder, 7);
    testCaptureRequestBody(builder, "request");
  }

  @Test
  void capturesRequestBody_binary() throws Exception {
    // not valid UTF-8, hexadecimal 0001fffe: it can only be captured as bytes
    byte[] body = {0x00, 0x01, (byte) 0xff, (byte) 0xfe};
    ServletTelemetryBuilder builder = ServletTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setCaptureRequestBodyMaxSize(builder, 32);

    Value<?> value = captureRequestBody(builder, body, "application/octet-stream");

    assertThat(value).isNotNull();
    assertThat(value.getType()).isEqualTo(ValueType.BYTES);
    assertThat(value.getValue()).isEqualTo(ByteBuffer.wrap(body));
  }

  private static void testCaptureRequestBody(
      ServletTelemetryBuilder builder, @Nullable String expected) throws Exception {

    Value<?> value = captureRequestBody(builder, "request-body".getBytes(UTF_8), null);
    if (expected == null) {
      assertThat(value).isNull();
    } else {
      assertThat(value).isNotNull();
      assertThat(value.getType()).isEqualTo(ValueType.STRING);
      assertThat(value.asString()).isEqualTo(expected);
    }
  }

  @Nullable
  private static Value<?> captureRequestBody(
      ServletTelemetryBuilder builder, byte[] body, @Nullable String contentType) throws Exception {
    Filter filter = builder.build().createFilter();

    HttpServletRequest request = requestWithBody(body, contentType);
    HttpServletResponse response = mock(HttpServletResponse.class);

    filter.doFilter(request, response, (req, res) -> readRequestBody(req, body));

    List<List<SpanData>> traces = testing.waitForTraces(1);

    return traces.get(0).get(0).getAttributes().get(BODY_ATTRIBUTE_KEY);
  }

  private static void readRequestBody(ServletRequest req, byte[] expected) throws IOException {
    ServletInputStream input = req.getInputStream();
    ByteArrayOutputStream content = new ByteArrayOutputStream();
    int value;
    while ((value = input.read()) != -1) {
      content.write(value);
    }
    // the application must always see the full body, whatever is captured in the span
    assertThat(content.toByteArray()).isEqualTo(expected);
  }

  private static HttpServletRequest requestWithBody(byte[] body, @Nullable String contentType)
      throws IOException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    // a mock does not keep attributes, so back them with a map
    Map<String, Object> attributes = new HashMap<>();
    doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
        .when(request)
        .setAttribute(anyString(), any());
    when(request.getAttribute(anyString()))
        .thenAnswer(invocation -> attributes.get((String) invocation.getArgument(0)));
    when(request.getMethod()).thenReturn("POST");
    when(request.getCharacterEncoding()).thenReturn("UTF-8");
    when(request.getHeader("Content-Type")).thenReturn(contentType);
    when(request.getInputStream())
        .thenReturn(
            new ServletInputStream() {
              private final ByteArrayInputStream input = new ByteArrayInputStream(body);

              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] b, int off, int len) {
                return input.read(b, off, len);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener readListener) {}
            });
    return request;
  }
}
