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

import static com.google.common.base.Preconditions.checkNotNull;
import static io.opentelemetry.contrib.http.core.HttpTraceConstants.INSTRUMENTATION_LIB_ID;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.correlationcontext.CorrelationContext;
import io.opentelemetry.correlationcontext.CorrelationContextManager;
import io.opentelemetry.correlationcontext.Entry;
import io.opentelemetry.correlationcontext.EntryMetadata.EntryTtl;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This helper class provides routine methods to instrument HTTP servers.
 *
 * @param <Q> the HTTP request entity.
 * @param <P> the HTTP response entity.
 * @param <C> the type of the tracing propagation carrier.
 */
public class HttpClientHandler<Q, P, C> extends AbstractHttpHandler<Q, P> {

  private static final Logger LOGGER = Logger.getLogger(HttpClientHandler.class.getName());

  private final Tracer tracer;
  private final CorrelationContextManager contextManager;
  private final HttpTextFormat.Setter<C> setter;
  private final HttpTextFormat<SpanContext> spanContextHttpTextFormat;
  private final HttpTextFormat<CorrelationContext> correlationContextHttpTextFormat;

  /**
   * Constructs a handler object.
   *
   * @param extractor the implementation of HTTP extractor which handles the particular classes used
   *     to hold HTTP request and response info in the library being instrumented.
   * @param setter the setter used to extract propagation information from the carrier.
   */
  public HttpClientHandler(HttpExtractor<Q, P> extractor, HttpTextFormat.Setter<C> setter) {
    super(extractor);
    checkNotNull(setter, "setter is required");
    this.tracer = OpenTelemetry.getTracerRegistry().get(INSTRUMENTATION_LIB_ID);
    this.contextManager = OpenTelemetry.getCorrelationContextManager();
    this.setter = setter;
    this.spanContextHttpTextFormat = this.tracer.getHttpTextFormat();
    this.correlationContextHttpTextFormat = this.contextManager.getHttpTextFormat();
  }

  /**
   * Constructs a handler object.
   *
   * @param extractor the implementation of HTTP extractor which handles the particular classes used
   *     to hold HTTP request and response info in the library being instrumented.
   * @param setter the setter used to extract propagation information from the carrier.
   * @param statusConverter the converter from HTTP status codes to OpenTelemetry statuses.
   * @param tracer the named OpenTelemetry tracer.
   * @param contextManager the OpenTelemetry correlation context manager
   * @param meter the named OpenTelemetry meter to use.
   */
  public HttpClientHandler(
      HttpExtractor<Q, P> extractor,
      HttpTextFormat.Setter<C> setter,
      StatusCodeConverter statusConverter,
      Tracer tracer,
      CorrelationContextManager contextManager,
      Meter meter) {
    super(extractor, statusConverter, meter);
    checkNotNull(setter, "setter is required");
    checkNotNull(tracer, "tracer is required");
    checkNotNull(contextManager, "contextManager is required");
    this.tracer = tracer;
    this.contextManager = contextManager;
    this.setter = setter;
    this.spanContextHttpTextFormat = this.tracer.getHttpTextFormat();
    this.correlationContextHttpTextFormat = this.contextManager.getHttpTextFormat();
  }

  /**
   * Instrument a request for tracing and stats before it is sent.
   *
   * <p>This method will create a span in current context to represent the HTTP call. The created
   * span will be serialized and propagated to the server.
   *
   * <p>The generated span will NOT be set as current context. User can control when to enter the
   * scope of this span. Use {@link AbstractHttpHandler#getSpanFromContext} to retrieve the span.
   *
   * @param parentSpan the parent {@link Span}. {@code null} indicates using current span.
   * @param parentContext the parent {@link CorrelationContext}. {@code null} indicates using
   *     current context.
   * @param carrier the entity that holds the HTTP information.
   * @param request the request entity.
   * @return the {@link HttpRequestContext} that contains stats and trace data associated with the
   *     request.
   */
  public HttpRequestContext handleStart(
      @Nullable Span parentSpan, @Nullable CorrelationContext parentContext, C carrier, Q request) {
    checkNotNull(carrier, "carrier");
    checkNotNull(request, "request");
    Span span = constructClientSpan(parentSpan, request);
    CorrelationContext propagatedContext = constructPropagatedContext(parentContext);

    SpanContext spanContext = span.getContext();
    if (spanContext.isValid()) {
      spanContextHttpTextFormat.inject(spanContext, carrier, setter);
      correlationContextHttpTextFormat.inject(propagatedContext, carrier, setter);
    }
    return getNewContext(span, propagatedContext);
  }

  /**
   * Close an HTTP span and records stats specific to the request.
   *
   * <p>This method will set status of the span and end it. Additionally it will record measurements
   * associated with the request.
   *
   * @param context the context from handle start
   * @param request the HTTP request entity.
   * @param response the HTTP response entity. {@code null} means invalid response.
   * @param error the error occurs when processing the response.
   */
  public void handleEnd(
      HttpRequestContext context,
      @Nullable Q request,
      @Nullable P response,
      @Nullable Throwable error) {
    checkNotNull(context, "context");
    int httpCode = extractor.getStatusCode(response);
    recordMeasurements(context, httpCode);
    endSpan(context.getSpan(), httpCode, error);
  }

  private Span constructClientSpan(@Nullable Span parent, Q request) {
    Span.Builder builder = tracer.spanBuilder(extractSpanName(request));
    Span span =
        builder
            .setParent(parent == null ? tracer.getCurrentSpan() : parent)
            .setSpanKind(Kind.CLIENT)
            .startSpan();
    addSpanRequestAttributes(span, request);
    return span;
  }

  private CorrelationContext constructPropagatedContext(CorrelationContext context) {
    CorrelationContext localContext =
        context == null ? contextManager.getCurrentContext() : context;
    CorrelationContext.Builder builder = contextManager.contextBuilder();
    if (localContext == null) {
      builder.setNoParent();
    } else {
      builder.setParent(localContext);
      for (Entry entry : localContext.getEntries()) {
        if (EntryTtl.UNLIMITED_PROPAGATION.equals(entry.getEntryMetadata().getEntryTtl())) {
          builder.put(entry.getKey(), entry.getValue(), entry.getEntryMetadata());
        }
      }
    }
    return builder.build();
  }

  @Override
  Logger getLogger() {
    return LOGGER;
  }
}
