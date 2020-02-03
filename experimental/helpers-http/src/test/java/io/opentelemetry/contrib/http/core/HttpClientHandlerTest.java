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
import io.opentelemetry.context.Scope;
import io.opentelemetry.correlationcontext.CorrelationContext;
import io.opentelemetry.correlationcontext.EntryKey;
import io.opentelemetry.correlationcontext.EntryMetadata;
import io.opentelemetry.correlationcontext.EntryMetadata.EntryTtl;
import io.opentelemetry.correlationcontext.EntryValue;
import io.opentelemetry.exporters.inmemory.InMemoryTracing;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.TracerSdkRegistry;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link HttpClientHandler}. */
@RunWith(JUnit4.class)
public class HttpClientHandlerTest {

  private static final Logger LOGGER = Logger.getLogger(HttpClientHandlerTest.class.getName());

  private InMemoryTracing inMemoryTracing;

  @Before
  public void configureTracing() {
    TraceConfig traceConfig =
        TraceConfig.getDefault().toBuilder().setSampler(Samplers.alwaysOn()).build();
    TracerSdkRegistry tracerSdk = (TracerSdkRegistry) OpenTelemetry.getTracerRegistry();
    LOGGER.log(Level.INFO, "Original trace config: " + tracerSdk.getActiveTraceConfig());
    tracerSdk.updateActiveTraceConfig(traceConfig);
    LOGGER.log(Level.INFO, "Updated trace config: " + tracerSdk.getActiveTraceConfig());
    inMemoryTracing = new InMemoryTracing(tracerSdk);
  }

  @After
  public void printSpans() {
    for (SpanData span : inMemoryTracing.getFinishedSpanItems()) {
      LOGGER.log(Level.FINEST, span.toString());
    }
  }

  @Test
  public void shouldStartAndEndSpanWhenParentIsAvailable() {
    Map<String, String> data = new HashMap<>();
    HttpClientHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpClientHandler<>(new TestOnlyMapHttpExtractor(), new TestOnlyMapGetterSetter());
    Span parentSpan = constructParentSpan();
    CorrelationContext parentContext = constructParentContext();
    LOGGER.log(Level.INFO, parentSpan.getContext().toString());
    try (Scope parent =
            OpenTelemetry.getTracerRegistry().get(INSTRUMENTATION_LIB_ID).withSpan(parentSpan);
        Scope contextScope =
            OpenTelemetry.getCorrelationContextManager().withContext(parentContext)) {
      HttpRequestContext context = handler.handleStart(null, null, data, data);
      Span currentSpan = handler.getSpanFromContext(context);
      LOGGER.log(Level.INFO, currentSpan.getContext().toString());
      try {
        assertThat(currentSpan.getContext().getTraceId())
            .isEqualTo(parentSpan.getContext().getTraceId());
        assertThat(currentSpan.getContext().getSpanId())
            .isNotEqualTo(parentSpan.getContext().getSpanId());
        assertThat(currentSpan.isRecording()).isTrue();
      } finally {
        handler.handleEnd(context, data, data, null);
      }
      assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
      printSpans();
    }
  }

  @Test
  public void shouldStartAndEndSpanWithProvidedParent() {
    Map<String, String> data = new HashMap<>();
    HttpClientHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpClientHandler<>(new TestOnlyMapHttpExtractor(), new TestOnlyMapGetterSetter());
    Span parentSpan = constructParentSpan();
    CorrelationContext parentContext = constructParentContext();
    LOGGER.log(Level.INFO, parentSpan.getContext().toString());
    HttpRequestContext context = handler.handleStart(parentSpan, parentContext, data, data);
    Span currentSpan = handler.getSpanFromContext(context);
    LOGGER.log(Level.INFO, currentSpan.getContext().toString());
    try {
      assertThat(currentSpan.getContext().getTraceId())
          .isEqualTo(parentSpan.getContext().getTraceId());
      assertThat(currentSpan.getContext().getSpanId())
          .isNotEqualTo(parentSpan.getContext().getSpanId());
      assertThat(currentSpan.isRecording()).isTrue();
    } finally {
      handler.handleEnd(context, data, data, null);
    }
    assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
    printSpans();
  }

  @Test
  public void shouldStartAndEndSpanWithNoParent() {
    Map<String, String> data = new HashMap<>();
    HttpClientHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpClientHandler<>(new TestOnlyMapHttpExtractor(), new TestOnlyMapGetterSetter());
    HttpRequestContext context = handler.handleStart(null, null, data, data);
    Span currentSpan = handler.getSpanFromContext(context);
    LOGGER.log(Level.INFO, currentSpan.getContext().toString());
    try {
      assertThat(currentSpan.getContext().isValid()).isTrue();
      assertThat(currentSpan.isRecording()).isTrue();
    } finally {
      handler.handleEnd(context, data, data, null);
    }
    assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
    printSpans();
  }

  @Test
  public void shouldAddExceptionInfoToSpanIfProvided() {
    Map<String, String> data = new HashMap<>();
    HttpClientHandler<Map<String, String>, Map<String, String>, Map<String, String>> handler =
        new HttpClientHandler<>(new TestOnlyMapHttpExtractor(), new TestOnlyMapGetterSetter());
    Span parentSpan = constructParentSpan();
    CorrelationContext parentContext = constructParentContext();
    LOGGER.log(Level.INFO, parentSpan.getContext().toString());
    try (Scope parent =
            OpenTelemetry.getTracerRegistry().get(INSTRUMENTATION_LIB_ID).withSpan(parentSpan);
        Scope contextScope =
            OpenTelemetry.getCorrelationContextManager().withContext(parentContext)) {
      HttpRequestContext context = handler.handleStart(null, null, data, data);
      Span currentSpan = handler.getSpanFromContext(context);
      LOGGER.log(Level.INFO, currentSpan.getContext().toString());
      IllegalStateException error = new IllegalStateException("this is a test");
      try {
        assertThat(currentSpan.getContext().getTraceId())
            .isEqualTo(parentSpan.getContext().getTraceId());
        assertThat(currentSpan.getContext().getSpanId())
            .isNotEqualTo(parentSpan.getContext().getSpanId());
        assertThat(currentSpan.isRecording()).isTrue();
      } finally {
        handler.handleEnd(context, data, data, error);
      }
      assertThat(inMemoryTracing.getFinishedSpanItems().isEmpty()).isFalse();
      printSpans();
    }
  }

  private static Span constructParentSpan() {
    return OpenTelemetry.getTracerRegistry()
        .get(INSTRUMENTATION_LIB_ID)
        .spanBuilder("/junit")
        .setNoParent()
        .setSpanKind(Kind.SERVER)
        .startSpan();
  }

  private static CorrelationContext constructParentContext() {
    return OpenTelemetry.getCorrelationContextManager()
        .contextBuilder()
        .put(
            EntryKey.create("testClass"),
            EntryValue.create("HttpClientHandlerTest"),
            EntryMetadata.create(EntryTtl.UNLIMITED_PROPAGATION))
        .build();
  }
}
