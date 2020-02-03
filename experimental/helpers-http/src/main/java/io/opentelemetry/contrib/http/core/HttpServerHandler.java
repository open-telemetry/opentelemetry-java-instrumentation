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
import io.opentelemetry.correlationcontext.EntryKey;
import io.opentelemetry.correlationcontext.EntryMetadata;
import io.opentelemetry.correlationcontext.EntryMetadata.EntryTtl;
import io.opentelemetry.correlationcontext.EntryValue;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.Tracestate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This helper class provides routine methods to instrument HTTP servers.
 *
 * @param <Q> the HTTP request entity.
 * @param <P> the HTTP response entity.
 * @param <C> the type of the tracing propagation carrier.
 */
public class HttpServerHandler<Q, P, C> extends AbstractHttpHandler<Q, P> {

  private static final Logger LOGGER = Logger.getLogger(HttpServerHandler.class.getName());

  private final Tracer tracer;
  private final CorrelationContextManager contextManager;
  private final HttpTextFormat.Getter<C> getter;
  private final boolean publicEndpoint;
  private final HttpTextFormat<SpanContext> spanContextHttpTextFormat;
  private final HttpTextFormat<CorrelationContext> correlationContextHttpTextFormat;

  /**
   * Constructs a handler object.
   *
   * @param extractor the implementation of HTTP extractor which handles the particular classes used
   *     to hold HTTP request and response info in the library being instrumented.
   * @param getter the getter used to extract propagation information from the carrier
   */
  public HttpServerHandler(HttpExtractor<Q, P> extractor, HttpTextFormat.Getter<C> getter) {
    super(extractor);
    checkNotNull(getter, "getter is required");
    this.tracer = OpenTelemetry.getTracerRegistry().get(INSTRUMENTATION_LIB_ID);
    this.contextManager = OpenTelemetry.getCorrelationContextManager();
    this.getter = getter;
    this.publicEndpoint = false;
    this.spanContextHttpTextFormat = this.tracer.getHttpTextFormat();
    this.correlationContextHttpTextFormat = this.contextManager.getHttpTextFormat();
  }

  /**
   * Constructs a handler object.
   *
   * @param extractor the implementation of HTTP extractor which handles the particular classes used
   *     to hold HTTP request and response info in the library being instrumented.
   * @param getter the getter used to extract propagation information from the carrier
   * @param statusConverter the converter from HTTP status codes to OpenTelemetry statuses.
   * @param tracer the named OpenTelemetry tracer.
   * @param contextManager the OpenTelemetry correlation context manager
   * @param meter the named OpenTelemetry meter to use.
   * @param publicEndpoint whether a new trace should be started for all requests or not
   */
  public HttpServerHandler(
      HttpExtractor<Q, P> extractor,
      HttpTextFormat.Getter<C> getter,
      StatusCodeConverter statusConverter,
      Tracer tracer,
      CorrelationContextManager contextManager,
      Meter meter,
      Boolean publicEndpoint) {
    super(extractor, statusConverter, meter);
    checkNotNull(getter, "getter is required");
    checkNotNull(tracer, "tracer is required");
    checkNotNull(contextManager, "contextManager is required");
    checkNotNull(publicEndpoint, "publicEndpoint is required");
    this.tracer = tracer;
    this.contextManager = contextManager;
    this.getter = getter;
    this.publicEndpoint = publicEndpoint;
    this.spanContextHttpTextFormat = this.tracer.getHttpTextFormat();
    this.correlationContextHttpTextFormat = this.contextManager.getHttpTextFormat();
  }

  /**
   * Instrument an incoming request before it is handled.
   *
   * <p>This method will create a span under the deserialized propagated parent context. If the
   * parent context is not present, the span will be created under the current context.
   *
   * <p>The generated span will NOT be set as current context. User can control when to enter the
   * scope of this span. Use getSpanFromContext to retrieve the span.
   *
   * @param carrier the entity that holds the HTTP information.
   * @param request the request entity.
   * @return the wrapper for all request info
   */
  public HttpRequestContext handleStart(C carrier, Q request) {
    checkNotNull(carrier, "carrier");
    checkNotNull(request, "request");
    Span.Builder spanBuilder = tracer.spanBuilder(extractSpanName(request));
    CorrelationContext.Builder dctxBuilder = contextManager.contextBuilder();

    SpanContext spanContext = null;
    try {
      spanContext = spanContextHttpTextFormat.extract(carrier, getter);
    } catch (IllegalArgumentException ignore) {
      // NoOp
    }
    CorrelationContext corrltContext = null;
    try {
      corrltContext = correlationContextHttpTextFormat.extract(carrier, getter);
    } catch (IllegalArgumentException ignore) {
      // NoOp
    }
    if (spanContext == null || publicEndpoint) {
      spanBuilder.setNoParent();
      dctxBuilder.setNoParent();
    } else {
      spanBuilder.setParent(spanContext);
      if (corrltContext == null) {
        dctxBuilder.setParent(contextManager.getCurrentContext());
      } else {
        dctxBuilder.setParent(corrltContext);
      }
      addTracestateToCorrelationContext(dctxBuilder, spanContext.getTracestate());
    }

    Span span = spanBuilder.setSpanKind(Kind.SERVER).startSpan();
    if (publicEndpoint && spanContext != null) {
      span.setAttribute(HttpTraceConstants.LINK_TYPE, HttpTraceConstants.LINK_ATTR_ORIGINATING);
    }

    addSpanRequestAttributes(span, request);
    return getNewContext(span, dctxBuilder.build());
  }

  private static void addTracestateToCorrelationContext(
      CorrelationContext.Builder dctxBuilder, Tracestate tracestate) {
    if (tracestate != null) {
      for (Tracestate.Entry entry : tracestate.getEntries()) {
        dctxBuilder.put(
            EntryKey.create(entry.getKey()),
            EntryValue.create(entry.getValue()),
            EntryMetadata.create(EntryTtl.UNLIMITED_PROPAGATION));
      }
    }
  }

  /**
   * Close an HTTP span and records events and measurements specific to the request.
   *
   * <p>This method will set status of the span and end it. Additionally it will record message
   * events for the span and record measurements associated with the request.
   *
   * @param context the context from handle start
   * @param request the request object
   * @param response the response object
   * @param error exception caught while waiting for or processing response
   */
  public void handleEnd(
      HttpRequestContext context, Q request, @Nullable P response, @Nullable Throwable error) {
    checkNotNull(context, "context");
    checkNotNull(request, "request");
    int httpCode = extractor.getStatusCode(response);
    recordMeasurements(context, httpCode);
    endSpan(context.getSpan(), httpCode, error);
  }

  @Override
  Logger getLogger() {
    return LOGGER;
  }
}
