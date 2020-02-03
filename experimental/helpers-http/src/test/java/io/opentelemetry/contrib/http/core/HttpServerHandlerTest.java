/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.contrib.http.core;

import static com.google.common.truth.Truth.assertThat;
import static io.opentelemetry.contrib.http.core.HttpTraceConstants.INSTRUMENTATION_LIB_ID;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.exporters.inmemory.InMemoryTracing;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.TracerSdkRegistry;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceId;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link HttpServerHandler}. */
@RunWith(JUnit4.class)
public class HttpServerHandlerTest {

  private static final Logger LOGGER = Logger.getLogger(HttpServerHandlerTest.class.getName());

  private InMemoryTracing inMemoryTracing;

  @Before
  public void configureTracing() {
    TraceConfig traceConfig =
        TraceConfig.getDefault().toBuilder().setSampler(Samplers.alwaysOn()).build();
    TracerSdkRegistry tracerSdk = (TracerSdkRegistry) OpenTelemetry.getTracerRegistry();
    tracerSdk.updateActiveTraceConfig(traceConfig);
    inMemoryTracing = new InMemoryTracing(tracerSdk);
  }

  @After
  public void printSpans() {
    for (SpanData span : inMemoryTracing.getFinishedSpanItems()) {
      LOGGER.log(Level.FINEST, span.toString());
    }
  }

  @Test
  public void shouldStartAndEndSpanWhenRemoteDataIsAvailable() {
    Map<String, String> data = new HashMap<>();
    String traceParent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    TraceId traceId = TraceId.fromLowerBase16(traceParent, 3);
    SpanId spanId = SpanId.fromLowerBase16(traceParent, 36);
    data.put("traceparent", traceParent);
    data.put("tracestate", "congo=t61rcWkgMzE");
    data.put(TestOnlyMapHttpExtractor.METHOD, "GET");
    String url = "https://api.example.com/widgets/6544557E-820C-4975-9E2A-C959B3F7D2FC";
    data.put(TestOnlyMapHttpExtractor.URL, url);
    data.put(TestOnlyMapHttpExtractor.PATH, "/widgets/6544557E-820C-4975-9E2A-C959B3F7D2FC");
    data.put(TestOnlyMapHttpExtractor.ROUTE, "/widgets/{id}");
    data.put(TestOnlyMapHttpExtractor.STATUS, "200");
    HttpServerHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpServerHandler<>(new TestOnlyMapHttpExtractor(), new TestOnlyMapGetterSetter());
    HttpRequestContext context = handler.handleStart(data, data);
    Span currentSpan = handler.getSpanFromContext(context);
    try {
      assertThat(currentSpan.getContext().getTraceId()).isEqualTo(traceId);
      assertThat(currentSpan.getContext().getSpanId()).isNotEqualTo(spanId);
      assertThat(currentSpan.isRecording()).isTrue();
    } finally {
      handler.handleEnd(context, data, data, null);
    }
    assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
  }

  @Test
  public void shouldStartAndEndSpanWhenRemoteDataIsAvailableButIsPublicEndpoint() {
    Map<String, String> data = new HashMap<>();
    String traceParent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    TraceId traceId = TraceId.fromLowerBase16(traceParent, 3);
    SpanId spanId = SpanId.fromLowerBase16(traceParent, 36);
    data.put("traceparent", traceParent);
    data.put("tracestate", "congo=t61rcWkgMzE");
    data.put(TestOnlyMapHttpExtractor.METHOD, "GET");
    String url = "https://api.example.com/widgets/6544557E-820C-4975-9E2A-C959B3F7D2FC";
    data.put(TestOnlyMapHttpExtractor.URL, url);
    data.put(TestOnlyMapHttpExtractor.PATH, "/widgets/6544557E-820C-4975-9E2A-C959B3F7D2FC");
    data.put(TestOnlyMapHttpExtractor.ROUTE, "/widgets/{id}");
    data.put(TestOnlyMapHttpExtractor.STATUS, "200");
    HttpServerHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpServerHandler<>(
            new TestOnlyMapHttpExtractor(),
            new TestOnlyMapGetterSetter(),
            new StatusCodeConverter(),
            OpenTelemetry.getTracerRegistry().get(INSTRUMENTATION_LIB_ID),
            OpenTelemetry.getCorrelationContextManager(),
            OpenTelemetry.getMeterRegistry().get(INSTRUMENTATION_LIB_ID),
            Boolean.TRUE);
    HttpRequestContext context = handler.handleStart(data, data);
    Span currentSpan = handler.getSpanFromContext(context);
    try {
      assertThat(traceId.equals(currentSpan.getContext().getTraceId())).isFalse();
      assertThat(currentSpan.getContext().getSpanId()).isNotEqualTo(spanId);
    } finally {
      handler.handleEnd(context, data, data, null);
    }
    assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
  }

  @Test
  public void shouldStartAndEndSpanWhenRemoteDataIsNotAvailable() {
    Map<String, String> data = new HashMap<>();
    data.put(TestOnlyMapHttpExtractor.METHOD, "GET");
    String url = "https://api.example.com/widgets/6544557E-820C-4975-9E2A-C959B3F7D2FC";
    data.put(TestOnlyMapHttpExtractor.URL, url);
    data.put(TestOnlyMapHttpExtractor.PATH, "/widgets/6544557E-820C-4975-9E2A-C959B3F7D2FC");
    data.put(TestOnlyMapHttpExtractor.ROUTE, "/widgets/{id}");
    data.put(TestOnlyMapHttpExtractor.STATUS, "200");
    HttpServerHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpServerHandler<>(new TestOnlyMapHttpExtractor(), new TestOnlyMapGetterSetter());
    HttpRequestContext context = handler.handleStart(data, data);
    Span currentSpan = handler.getSpanFromContext(context);
    try {
      assertThat(currentSpan.getContext().isValid()).isTrue();
    } finally {
      handler.handleEnd(context, data, data, null);
    }
    assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
  }

  @Test
  public void shouldAddExceptionInfoToSpanIfProvided() {
    Map<String, String> data = new HashMap<>();
    String traceParent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    TraceId traceId = TraceId.fromLowerBase16(traceParent, 3);
    SpanId spanId = SpanId.fromLowerBase16(traceParent, 36);
    data.put("traceparent", traceParent);
    data.put("tracestate", "congo=t61rcWkgMzE");
    data.put(TestOnlyMapHttpExtractor.METHOD, "POST");
    String url = "https://api.example.com/widgets";
    data.put(TestOnlyMapHttpExtractor.URL, url);
    data.put(TestOnlyMapHttpExtractor.PATH, "/widgets");
    data.put(TestOnlyMapHttpExtractor.ROUTE, "/widgets");
    data.put(TestOnlyMapHttpExtractor.STATUS, "409");
    HttpServerHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpServerHandler<>(
            new TestOnlyMapHttpExtractor(),
            new TestOnlyMapGetterSetter(),
            new ExtendedStatusCodeConverter(),
            OpenTelemetry.getTracerRegistry().get(INSTRUMENTATION_LIB_ID),
            OpenTelemetry.getCorrelationContextManager(),
            OpenTelemetry.getMeterRegistry().get(INSTRUMENTATION_LIB_ID),
            Boolean.FALSE);
    HttpRequestContext context = handler.handleStart(data, data);
    Span currentSpan = handler.getSpanFromContext(context);
    IllegalStateException error = new IllegalStateException("this is a test");
    try {
      assertThat(currentSpan.getContext().getTraceId()).isEqualTo(traceId);
      assertThat(currentSpan.getContext().getSpanId()).isNotEqualTo(spanId);
    } finally {
      handler.handleEnd(context, data, data, error);
    }
    assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
  }
}
