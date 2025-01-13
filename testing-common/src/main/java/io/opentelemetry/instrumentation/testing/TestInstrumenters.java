/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import java.util.List;
import javax.annotation.Nullable;

/** {@link Instrumenter}s to use when executing test code. */
final class TestInstrumenters {

  private final Instrumenter<String, Void> instrumenter;
  private final Instrumenter<String, Void> httpClientInstrumenter;
  private final Instrumenter<String, Void> httpServerInstrumenter;

  TestInstrumenters(OpenTelemetry openTelemetry) {
    instrumenter =
        Instrumenter.<String, Void>builder(openTelemetry, "test", name -> name)
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
    httpClientInstrumenter =
        Instrumenter.<String, Void>builder(openTelemetry, "test", name -> name)
            // cover both semconv and span-kind strategies
            .addAttributesExtractor(new SpanKeyAttributesExtractor(SpanKey.HTTP_CLIENT))
            .addAttributesExtractor(new SpanKeyAttributesExtractor(SpanKey.KIND_CLIENT))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
    httpServerInstrumenter =
        Instrumenter.<String, Void>builder(
                openTelemetry, "test", HttpSpanNameExtractor.create(HttpServerGetter.INSTANCE))
            // cover both semconv and span-kind strategies
            .addAttributesExtractor(HttpServerAttributesExtractor.create(HttpServerGetter.INSTANCE))
            .addAttributesExtractor(new SpanKeyAttributesExtractor(SpanKey.KIND_SERVER))
            .addContextCustomizer(HttpServerRoute.create(HttpServerGetter.INSTANCE))
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  /**
   * Runs the provided {@code callback} inside the scope of an INTERNAL span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return runWithInstrumenter(spanName, instrumenter, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithHttpClientSpan(String spanName, ThrowingSupplier<T, E> callback)
      throws E {
    return runWithInstrumenter(spanName, httpClientInstrumenter, callback);
  }

  /**
   * Runs the provided {@code callback} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  <T, E extends Throwable> T runWithHttpServerSpan(ThrowingSupplier<T, E> callback) throws E {
    return runWithInstrumenter("ignored", httpServerInstrumenter, callback);
  }

  /** Runs the provided {@code callback} inside the scope of a non-recording span. */
  <T, E extends Throwable> T runWithNonRecordingSpan(ThrowingSupplier<T, E> callback) throws E {
    SpanContext spanContext =
        SpanContext.create(
            "12345678123456781234567812345678",
            "1234567812345678",
            TraceFlags.getDefault(),
            TraceState.getDefault());
    try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
      return callback.get();
    }
  }

  private static <T, E extends Throwable> T runWithInstrumenter(
      String spanName, Instrumenter<String, Void> instrumenter, ThrowingSupplier<T, E> callback)
      throws E {
    Context context = instrumenter.start(Context.current(), spanName);

    T result;
    try (Scope ignored = context.makeCurrent()) {
      result = callback.get();
    } catch (Throwable t) {
      instrumenter.end(context, spanName, null, t);
      throw t;
    }
    instrumenter.end(context, spanName, null, null);
    return result;
  }

  private static final class SpanKeyAttributesExtractor
      implements AttributesExtractor<String, Void>, SpanKeyProvider {

    private final SpanKey spanKey;

    private SpanKeyAttributesExtractor(SpanKey spanKey) {
      this.spanKey = spanKey;
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, String s) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        String s,
        @Nullable Void unused,
        @Nullable Throwable error) {}

    @Override
    public SpanKey internalGetSpanKey() {
      return spanKey;
    }
  }

  private enum HttpServerGetter implements HttpServerAttributesGetter<String, Void> {
    INSTANCE;

    @Override
    public String getHttpRequestMethod(String unused) {
      return "GET";
    }

    @Override
    public List<String> getHttpRequestHeader(String unused, String name) {
      return emptyList();
    }

    @Nullable
    @Override
    public Integer getHttpResponseStatusCode(
        String unused, Void unused2, @Nullable Throwable error) {
      return null;
    }

    @Override
    public List<String> getHttpResponseHeader(String unused, Void unused2, String name) {
      return emptyList();
    }

    @Nullable
    @Override
    public String getUrlScheme(String unused) {
      return null;
    }

    @Nullable
    @Override
    public String getUrlPath(String s) {
      return null;
    }

    @Nullable
    @Override
    public String getUrlQuery(String s) {
      return null;
    }
  }
}
