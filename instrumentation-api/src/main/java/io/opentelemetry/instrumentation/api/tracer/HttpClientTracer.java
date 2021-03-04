/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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

  protected HttpClientTracer() {
    super();
  }

  /**
   * Prefer to pass in an OpenTelemetry instance, rather than just a Tracer, so you don't have to
   * use the GlobalOpenTelemetry Propagator instance.
   *
   * @deprecated prefer to pass in an OpenTelemetry instance, instead.
   */
  @Deprecated
  protected HttpClientTracer(Tracer tracer) {
    super(tracer);
  }

  protected HttpClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  protected abstract String method(REQUEST request);

  @Nullable
  protected abstract URI url(REQUEST request) throws URISyntaxException;

  @Nullable
  protected String flavor(REQUEST request) {
    // This is de facto standard nowadays, so let us use it, unless overridden
    return "1.1";
  }

  @Nullable
  protected abstract Integer status(RESPONSE response);

  @Nullable
  protected abstract String requestHeader(REQUEST request, String name);

  @Nullable
  protected abstract String responseHeader(RESPONSE response, String name);

  protected abstract TextMapSetter<CARRIER> getSetter();

  public boolean shouldStartSpan(Context parentContext) {
    return shouldStartSpan(parentContext, CLIENT);
  }

  public Context startSpan(Context parentContext, REQUEST request, CARRIER carrier) {
    return startSpan(parentContext, request, carrier, -1);
  }

  public Context startSpan(
      SpanKind kind, Context parentContext, REQUEST request, CARRIER carrier, long startTimeNanos) {
    Span span =
        internalStartSpan(
            kind, parentContext, request, spanNameForRequest(request), startTimeNanos);
    Context context = withClientSpan(parentContext, span);
    inject(context, carrier);
    return context;
  }

  public Context startSpan(
      Context parentContext, REQUEST request, CARRIER carrier, long startTimeNanos) {
    return startSpan(SpanKind.CLIENT, parentContext, request, carrier, startTimeNanos);
  }

  protected void inject(Context context, CARRIER carrier) {
    TextMapSetter<CARRIER> setter = getSetter();
    if (setter == null) {
      throw new IllegalStateException(
          "getSetter() not defined but calling inject(), either getSetter must be implemented or the scope should be setup manually");
    }
    inject(context, carrier, setter);
  }

  public void end(Context context, RESPONSE response) {
    end(context, response, -1);
  }

  public void end(Context context, RESPONSE response, long endTimeNanos) {
    Span span = Span.fromContext(context);
    onResponse(span, response);
    super.end(context, endTimeNanos);
  }

  public void endExceptionally(Context context, RESPONSE response, Throwable throwable) {
    endExceptionally(context, response, throwable, -1);
  }

  public void endExceptionally(
      Context context, RESPONSE response, Throwable throwable, long endTimeNanos) {
    Span span = Span.fromContext(context);
    onResponse(span, response);
    super.endExceptionally(context, throwable, endTimeNanos);
  }

  // TODO (trask) see if we can reduce the number of end..() variants
  //  see https://github.com/open-telemetry/opentelemetry-java-instrumentation
  //                        /pull/1893#discussion_r542111699
  public void endMaybeExceptionally(
      Context context, RESPONSE response, @Nullable Throwable throwable) {
    if (throwable != null) {
      endExceptionally(context, throwable);
    } else {
      end(context, response);
    }
  }

  private Span internalStartSpan(
      SpanKind kind, Context parentContext, REQUEST request, String name, long startTimeNanos) {
    SpanBuilder spanBuilder = tracer.spanBuilder(name).setSpanKind(kind).setParent(parentContext);
    if (startTimeNanos > 0) {
      spanBuilder.setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS);
    }
    Span span = spanBuilder.startSpan();
    onRequest(span, request);
    return span;
  }

  protected void onRequest(Span span, REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setAttribute(SemanticAttributes.NET_TRANSPORT, "IP.TCP");
      span.setAttribute(SemanticAttributes.HTTP_METHOD, method(request));
      span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, requestHeader(request, USER_AGENT));

      setFlavor(span, request);
      setUrl(span, request);
    }
  }

  private void setFlavor(Span span, REQUEST request) {
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

  private void setUrl(Span span, REQUEST request) {
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

  protected void onResponse(Span span, RESPONSE response) {
    assert span != null;
    if (response != null) {
      Integer status = status(response);
      if (status != null) {
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (long) status);
        span.setStatus(HttpStatusConverter.statusFromHttpStatus(status));
      }
    }
  }

  protected String spanNameForRequest(REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }
}
