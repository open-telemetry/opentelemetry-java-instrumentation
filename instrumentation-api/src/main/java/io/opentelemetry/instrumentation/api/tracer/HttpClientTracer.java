/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.api.tracer.utils.SpanAttributeSetter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpClientTracer<REQUEST, CARRIER, RESPONSE> extends BaseTracer {

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

  protected abstract TextMapPropagator.Setter<CARRIER> getSetter();

  protected HttpClientTracer() {
    super();
  }

  protected HttpClientTracer(Tracer tracer) {
    super(tracer);
  }

  // if this resulting operation needs to be manually propagated, that should be done outside of
  // this method
  public HttpClientOperation<RESPONSE> startOperation(REQUEST request, CARRIER carrier) {
    return startOperation(request, carrier, -1);
  }

  // if this resulting operation needs to be manually propagated, that should be done outside of
  // this method
  public HttpClientOperation<RESPONSE> startOperation(
      REQUEST request, CARRIER carrier, long startTimeNanos) {
    Context parentContext = Context.current();
    if (!shouldStartSpan(parentContext)) {
      return HttpClientOperation.noop();
    }
    Span span = spanBuilder(parentContext, request, startTimeNanos).startSpan();
    Context context = withClientSpan(parentContext, span);
    inject(carrier, context);
    return newOperation(context, parentContext);
  }

  protected boolean shouldStartSpan(Context parentContext) {
    return parentContext.get(CONTEXT_CLIENT_SPAN_KEY) == null;
  }

  protected SpanBuilder spanBuilder(Context parentContext, REQUEST request) {
    return spanBuilder(parentContext, request, -1);
  }

  protected SpanBuilder spanBuilder(Context parentContext, REQUEST request, long startTimeNanos) {
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanNameForRequest(request))
            .setSpanKind(Kind.CLIENT)
            .setParent(parentContext);
    if (startTimeNanos > 0) {
      spanBuilder.setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS);
    }
    onRequest(spanBuilder::setAttribute, request);
    return spanBuilder;
  }

  protected void onRequest(SpanAttributeSetter span, REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setAttribute(SemanticAttributes.NET_TRANSPORT, "IP.TCP");
      span.setAttribute(SemanticAttributes.HTTP_METHOD, method(request));
      span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, requestHeader(request, USER_AGENT));

      setFlavor(span, request);
      setUrl(span, request);
    }
  }

  protected Context withClientSpan(Context parentContext, Span span) {
    return parentContext.with(span).with(CONTEXT_CLIENT_SPAN_KEY, span);
  }

  protected void inject(CARRIER carrier, Context context) {
    Setter<CARRIER> setter = getSetter();
    if (setter == null) {
      throw new IllegalStateException(
          "getSetter() not defined but calling startScope(),"
              + " either getSetter must be implemented or the scope should be setup manually");
    }
    OpenTelemetry.getGlobalPropagators().getTextMapPropagator().inject(context, carrier, setter);
  }

  protected DefaultHttpClientOperation<RESPONSE> newOperation(
      Context context, Context parentContext) {
    return new DefaultHttpClientOperation<>(context, parentContext, this);
  }

  void end(Context context, RESPONSE response) {
    end(context, response, -1);
  }

  void end(Context context, RESPONSE response, long endTimeNanos) {
    Span span = Span.fromContext(context);
    onResponse(span, response);
    super.end(span, endTimeNanos);
  }

  void endExceptionally(Context context, RESPONSE response, Throwable throwable) {
    endExceptionally(context, response, throwable, -1);
  }

  void endExceptionally(
      Context context, RESPONSE response, Throwable throwable, long endTimeNanos) {
    Span span = Span.fromContext(context);
    onResponse(span, response);
    super.endExceptionally(span, throwable, endTimeNanos);
  }

  void endExceptionally(Context context, Throwable throwable) {
    Span span = Span.fromContext(context);
    super.endExceptionally(span, throwable, -1);
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

  protected Span onResponse(Span span, RESPONSE response) {
    assert span != null;
    if (response != null) {
      Integer status = status(response);
      if (status != null) {
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (long) status);
        span.setStatus(HttpStatusConverter.statusFromHttpStatus(status));
      }
    }
    return span;
  }

  protected String spanNameForRequest(REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }
}
