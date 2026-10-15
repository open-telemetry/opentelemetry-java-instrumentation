/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ServletBodyCaptureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // TODO: to be replaced with 1.45.0 semconv incubating attribute when available
  private static final AttributeKey<String> BODY_ATTRIBUTE_KEY =
      stringKey("http.request.body.content");

  @Test
  void doesNotCaptureRequestBodyByDefault() throws Exception {
    Filter filter = ServletTelemetry.builder(testing.getOpenTelemetry()).build().createFilter();

    HttpServletRequest request = requestWithBody();
    HttpServletResponse response = mock(HttpServletResponse.class);

    filter.doFilter(request, response, (req, res) -> readRequestBody(req));

    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces.get(0).get(0).getAttributes().asMap().keySet())
        .doesNotContain(BODY_ATTRIBUTE_KEY);
  }

  private static void readRequestBody(ServletRequest req) throws IOException {
    ServletInputStream input = req.getInputStream();
    int value;
    do {
      value = input.read();
    } while (value > 0);
  }

  @Test
  void capturesRequestBodyWhenEnabled() throws Exception {
    testCaptureRequestBody(32, "request-body");
  }

  @Test
  void capturesRequestBodyWhenEnabledWithMaxSize() throws Exception {
    testCaptureRequestBody(7, "request");
  }

  private static void testCaptureRequestBody(int maxSize, String expected) throws Exception {
    ServletTelemetryBuilder builder = ServletTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setCaptureRequestBodyMaxSize(builder, maxSize);
    Filter filter = builder.build().createFilter();

    HttpServletRequest request = requestWithBody();
    HttpServletResponse response = mock(HttpServletResponse.class);

    filter.doFilter(request, response, (req, res) -> readRequestBody(req));

    List<List<SpanData>> traces = testing.waitForTraces(1);

    assertThat(traces.get(0).get(0).getAttributes().get(BODY_ATTRIBUTE_KEY)).isEqualTo(expected);
  }

  private static HttpServletRequest requestWithBody() throws IOException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    // a mock does not keep attributes, so back them with a map
    Map<String, Object> attributes = new HashMap<>();
    doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
        .when(request)
        .setAttribute(anyString(), any());
    when(request.getAttribute(anyString()))
        .thenAnswer(invocation -> attributes.get((String) invocation.getArgument(0)));
    when(request.getMethod()).thenReturn("POST");
    when(request.getInputStream())
        .thenReturn(
            new ServletInputStream() {
              private final ByteArrayInputStream input =
                  new ByteArrayInputStream("request-body".getBytes(UTF_8));

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
