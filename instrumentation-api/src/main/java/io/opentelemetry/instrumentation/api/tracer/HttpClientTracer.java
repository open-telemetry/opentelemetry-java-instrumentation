/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.api.tracer.utils.SpanAttributeSetter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpClientTracer<REQUEST, RESPONSE> extends BaseTracer {

  private static final Logger log = LoggerFactory.getLogger(HttpClientTracer.class);

  public static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected static final String USER_AGENT = "User-Agent";

  protected abstract String method(REQUEST request);

  @Nullable
  protected abstract URI url(REQUEST request) throws URISyntaxException;

  @Nullable
  protected String flavor(REQUEST request) {
    // This is de facto standard nowadays, so let us use it, unless overridden
    return "1.1";
  }

  protected abstract Integer status(RESPONSE response);

  @Nullable
  protected abstract String requestHeader(REQUEST request, String name);

  @Nullable
  protected abstract String responseHeader(RESPONSE response, String name);

  protected HttpClientTracer() {
    super();
  }

  protected HttpClientTracer(Tracer tracer) {
    super(tracer);
  }

  // TODO (trask) is this a good idea or no?
  // NOTE: in general, the following methods are protected, and subclasses should expose the
  // relevant methods as public. this is so that consumers of the tracers will only see the methods
  // that are relevant to them

  // if this resulting operation needs to be manually propagated, that should be done outside of
  // this method
  protected final HttpClientOperation startOperation(
      REQUEST request, TextMapPropagator.Setter<REQUEST> setter) {
    return startOperation(request, request, setter);
  }

  // if this resulting operation needs to be manually propagated, that should be done outside of
  // this method
  protected final <CARRIER> HttpClientOperation startOperation(
      REQUEST request, CARRIER carrier, TextMapPropagator.Setter<CARRIER> setter) {
    return startOperation(request, carrier, setter, -1);
  }

  // if this resulting operation needs to be manually propagated, that should be done outside of
  // this method
  protected final <CARRIER> HttpClientOperation startOperation(
      REQUEST request,
      CARRIER carrier,
      TextMapPropagator.Setter<CARRIER> setter,
      long startTimeNanos) {
    return internalStartOperation(request, carrier, setter, startTimeNanos);
  }

  private <CARRIER> HttpClientOperation internalStartOperation(
      REQUEST request,
      CARRIER carrier,
      TextMapPropagator.Setter<CARRIER> setter,
      long startTimeNanos) {
    Context parentContext = Context.current();
    if (inClientSpan(parentContext)) {
      return HttpClientOperation.noop();
    }
    String spanName = spanNameForRequest(request);
    SpanBuilder spanBuilder = spanBuilder(parentContext, request, spanName, startTimeNanos);
    Context context = withClientSpan(parentContext, spanBuilder.startSpan());
    OpenTelemetry.getGlobalPropagators().getTextMapPropagator().inject(context, carrier, setter);
    return newOperation(context, parentContext);
  }

  protected final boolean inClientSpan(Context parentContext) {
    return parentContext.get(CONTEXT_CLIENT_SPAN_KEY) != null;
  }

  protected final SpanBuilder spanBuilder(Context parentContext, REQUEST request) {
    return spanBuilder(parentContext, request, spanNameForRequest(request));
  }

  // override onRequest() if you want to capture more request attributes
  protected final SpanBuilder spanBuilder(Context parentContext, REQUEST request, String spanName) {
    return spanBuilder(parentContext, request, spanName, -1);
  }

  // override onRequest() if you want to capture more request attributes
  protected final SpanBuilder spanBuilder(
      Context parentContext, REQUEST request, String spanName, long startTimeNanos) {
    SpanBuilder spanBuilder =
        tracer.spanBuilder(spanName).setSpanKind(Kind.CLIENT).setParent(parentContext);
    if (startTimeNanos > 0) {
      spanBuilder.setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS);
    }
    onRequest(spanBuilder::setAttribute, request);
    return spanBuilder;
  }

  protected final Context withClientSpan(Context parentContext, Span span) {
    return parentContext.with(span).with(CONTEXT_CLIENT_SPAN_KEY, span);
  }

  // TODO (trask) inline?
  protected final HttpClientOperation newOperation(Context context, Context parentContext) {
    return HttpClientOperation.create(context, parentContext);
  }

  protected void onRequest(SpanBuilder spanBuilder, REQUEST request) {
    onRequest(spanBuilder::setAttribute, request);
  }

  protected void onRequest(HttpClientOperation operation, REQUEST request) {
    onRequest(operation.getSpan()::setAttribute, request);
  }

  private void onRequest(SpanAttributeSetter span, REQUEST request) {
    if (request != null) {
      span.setAttribute(SemanticAttributes.NET_TRANSPORT, "IP.TCP");
      span.setAttribute(SemanticAttributes.HTTP_METHOD, method(request));
      span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, requestHeader(request, USER_AGENT));

      setFlavor(span, request);
      setUrl(span, request);
    }
  }

  public void end(HttpClientOperation operation, RESPONSE response) {
    end(operation, response, -1);
  }

  public void end(HttpClientOperation operation, RESPONSE response, long endTimeNanos) {
    // TODO (trask) require response to be non-null here
    Span span = operation.getSpan();
    onResponse(span, response);
    super.end(span, endTimeNanos);
  }

  public void endExceptionally(HttpClientOperation operation, Throwable throwable) {
    checkNotNull(throwable);
    endExceptionally(operation, throwable, null);
  }

  public void endExceptionally(
      HttpClientOperation operation, Throwable throwable, RESPONSE response) {
    endExceptionally(operation, throwable, response, -1);
  }

  public void endExceptionally(
      HttpClientOperation operation, Throwable throwable, RESPONSE response, long endTimeNanos) {
    Span span = operation.getSpan();
    if (response != null) {
      onResponse(span, response);
    }
    super.endExceptionally(span, throwable, endTimeNanos);
  }

  /** Convenience method primarily for bytecode instrumentation. */
  public void endMaybeExceptionally(
      HttpClientOperation operation, RESPONSE response, Throwable throwable) {
    if (throwable != null) {
      endExceptionally(operation, throwable);
    } else {
      end(operation, response);
    }
  }

  // TODO (trask) should this and onRequest() above take Context instead of Span?
  //  to be more generally useful somehow?
  // TODO (trask) could make this not public for now, only 2 subclasses are using, and those uses
  //  could be inlined into advice
  protected void onResponse(Span span, RESPONSE response) {
    Integer status = status(response);
    if (status != null) {
      span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (long) status);
      StatusCode statusCode = HttpStatusConverter.statusFromHttpStatus(status);
      if (statusCode == StatusCode.ERROR) {
        span.setStatus(statusCode);
      }
    }
  }

  private void setFlavor(SpanAttributeSetter span, REQUEST request) {
    String flavor = flavor(request);
    if (flavor == null) {
      return;
    }

    String httpProtocolPrefix = "HTTP/";
    if (flavor.startsWith(httpProtocolPrefix)) {
      flavor = flavor.substring(httpProtocolPrefix.length());
    }

    span.setAttribute(SemanticAttributes.HTTP_FLAVOR, flavor);
  }

  private void setUrl(SpanAttributeSetter span, REQUEST request) {
    try {
      URI url = url(request);
      if (url != null) {
        NetPeerUtils.INSTANCE.setNetPeer(span, url.getHost(), null, url.getPort());
        span.setAttribute(SemanticAttributes.HTTP_URL, url.toString());
      }
    } catch (Exception e) {
      log.debug("Error tagging url", e);
    }
  }

  private String spanNameForRequest(REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }

  private static void checkNotNull(Object obj) {
    if (obj == null) {
      throw new NullPointerException();
    }
  }
}
