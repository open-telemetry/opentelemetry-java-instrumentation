/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.Span.Kind.SERVER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

// TODO In search for a better home package

/**
 * Base class for implementing Tracers for HTTP servers. It has 3 types that must be specified by
 * subclasses:
 *
 * @param <REQUEST> - The specific type for HTTP requests
 * @param <RESPONSE> - The specific type for HTTP responses
 * @param <CONNECTION> - The specific type of HTTP connection, used to get peer address information
 *     and HTTP flavor. Use Void if your subclass does not need the connection for peerHostIP,
 *     peerPort, or flavor.
 * @param <STORAGE> - Implementation specific storage type for attaching/getting the server context.
 *     Use Void if your subclass does not have an implementation specific storage need.
 */
public abstract class HttpServerTracer<REQUEST, RESPONSE, CONNECTION, STORAGE> extends BaseTracer {

  // the class name is part of the attribute name, so that it will be shaded when used in javaagent
  // instrumentation, and won't conflict with usage outside javaagent instrumentation
  public static final String CONTEXT_ATTRIBUTE = HttpServerTracer.class.getName() + ".Context";

  protected static final String USER_AGENT = "User-Agent";

  public HttpServerTracer() {
    super();
  }

  public HttpServerTracer(Tracer tracer) {
    super(tracer);
  }

  public Context startSpan(REQUEST request, CONNECTION connection, STORAGE storage, Method origin) {
    String spanName = spanNameForMethod(origin);
    return startSpan(request, connection, storage, spanName);
  }

  public Context startSpan(
      REQUEST request, CONNECTION connection, STORAGE storage, String spanName) {
    return startSpan(request, connection, storage, spanName, -1);
  }

  public Context startSpan(
      REQUEST request,
      CONNECTION connection,
      @Nullable STORAGE storage,
      String spanName,
      long startTimestamp) {

    // not checking if inside of nested SERVER span because of concerns about context leaking
    // and so always starting with a clean context here

    // also we can't conditionally start a span in this method, because the caller won't know
    // whether to call end() or not on the Span in the returned Context

    Context parentContext = extract(request, getGetter());
    SpanBuilder builder = tracer.spanBuilder(spanName).setSpanKind(SERVER).setParent(parentContext);

    if (startTimestamp >= 0) {
      builder.setStartTimestamp(startTimestamp, TimeUnit.NANOSECONDS);
    }

    Span span = builder.startSpan();
    onConnection(span, connection);
    onRequest(span, request);
    onConnectionAndRequest(span, connection, request);

    Context context = parentContext.with(CONTEXT_SERVER_SPAN_KEY, span).with(span);
    attachServerContext(context, storage);

    return context;
  }

  /**
   * Convenience method. Delegates to {@link #end(Context, Object, long)}, passing {@code timestamp}
   * value of {@code -1}.
   */
  // TODO should end methods remove SPAN attribute from request as well?
  public void end(Context context, RESPONSE response) {
    end(context, response, -1);
  }

  // TODO should end methods remove SPAN attribute from request as well?
  public void end(Context context, RESPONSE response, long timestamp) {
    Span span = Span.fromContext(context);
    setStatus(span, responseStatus(response));
    endSpan(span, timestamp);
  }

  /**
   * Convenience method. Delegates to {@link #endExceptionally(Context, Throwable, Object)}, passing
   * {@code response} value of {@code null}.
   */
  public void endExceptionally(Context context, Throwable throwable) {
    endExceptionally(context, throwable, null);
  }

  /**
   * Convenience method. Delegates to {@link #endExceptionally(Context, Throwable, Object, long)},
   * passing {@code timestamp} value of {@code -1}.
   */
  public void endExceptionally(Context context, Throwable throwable, RESPONSE response) {
    endExceptionally(context, throwable, response, -1);
  }

  /**
   * If {@code response} is {@code null}, the {@code http.status_code} will be set to {@code 500}
   * and the {@link Span} status will be set to {@link io.opentelemetry.api.trace.StatusCode#ERROR}.
   */
  public void endExceptionally(
      Context context, Throwable throwable, RESPONSE response, long timestamp) {
    Span span = Span.fromContext(context);
    onError(span, unwrapThrowable(throwable));
    if (response == null) {
      setStatus(span, 500);
    } else {
      setStatus(span, responseStatus(response));
    }
    endSpan(span, timestamp);
  }

  public Span getServerSpan(STORAGE storage) {
    Context attachedContext = getServerContext(storage);
    return attachedContext == null ? null : attachedContext.get(CONTEXT_SERVER_SPAN_KEY);
  }

  /**
   * Returns context stored to the given request-response-loop storage by {@link
   * #attachServerContext(Context, Object)}.
   */
  @Nullable
  public abstract Context getServerContext(STORAGE storage);

  protected void onConnection(Span span, CONNECTION connection) {
    span.setAttribute(SemanticAttributes.NET_PEER_IP, peerHostIP(connection));
    Integer port = peerPort(connection);
    // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
    if (port != null && port > 0) {
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) port);
    }
  }

  protected void onRequest(Span span, REQUEST request) {
    span.setAttribute(SemanticAttributes.HTTP_METHOD, method(request));
    span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, requestHeader(request, USER_AGENT));

    setUrl(span, request);

    // TODO set resource name from URL.
  }

  /*
  https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md

  HTTP semantic convention recommends setting http.scheme, http.host, http.target attributes
  instead of http.url because it "is usually not readily available on the server side but would have
  to be assembled in a cumbersome and sometimes lossy process from other information".

  But in Java world there is no standard way to access "The full request target as passed in a HTTP request line or equivalent"
  which is the recommended value for http.target attribute. Therefore we cannot use any of the
  recommended combinations of attributes and are forced to use http.url.
   */
  private void setUrl(Span span, REQUEST request) {
    span.setAttribute(SemanticAttributes.HTTP_URL, url(request));
  }

  protected void onConnectionAndRequest(Span span, CONNECTION connection, REQUEST request) {
    String flavor = flavor(connection, request);
    if (flavor != null) {
      span.setAttribute(SemanticAttributes.HTTP_FLAVOR, flavor);
    }
    span.setAttribute(SemanticAttributes.HTTP_CLIENT_IP, clientIP(connection, request));
  }

  private String clientIP(CONNECTION connection, REQUEST request) {
    // try Forwarded
    String forwarded = requestHeader(request, "Forwarded");
    if (forwarded != null) {
      forwarded = extractForwardedFor(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-For
    forwarded = requestHeader(request, "X-Forwarded-For");
    if (forwarded != null) {
      // may be split by ,
      int endIndex = forwarded.indexOf(',');
      if (endIndex > 0) {
        forwarded = forwarded.substring(0, endIndex);
      }
      if (!forwarded.isEmpty()) {
        return forwarded;
      }
    }

    // fallback to peer IP if there are no proxy headers
    return peerHostIP(connection);
  }

  // VisibleForTesting
  static String extractForwardedFor(String forwarded) {
    int start = forwarded.toLowerCase().indexOf("for=");
    if (start < 0) {
      return null;
    }
    start += 4; // start is now the index after for=
    if (start >= forwarded.length() - 1) { // the value after for= must not be empty
      return null;
    }
    for (int i = start; i < forwarded.length() - 1; i++) {
      char c = forwarded.charAt(i);
      if (c == ',' || c == ';') {
        if (i == start) { // empty string
          return null;
        }
        return forwarded.substring(start, i);
      }
    }
    return forwarded.substring(start);
  }

  private static void setStatus(Span span, int status) {
    span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (long) status);
    // TODO status_message
    // See https://github.com/open-telemetry/opentelemetry-specification/issues/950
    span.setStatus(HttpStatusConverter.statusFromHttpStatus(status));
  }

  private static void endSpan(Span span, long timestamp) {
    if (timestamp >= 0) {
      span.end(timestamp, TimeUnit.NANOSECONDS);
    } else {
      span.end();
    }
  }

  @Nullable
  protected abstract Integer peerPort(CONNECTION connection);

  @Nullable
  protected abstract String peerHostIP(CONNECTION connection);

  protected abstract String flavor(CONNECTION connection, REQUEST request);

  protected abstract TextMapPropagator.Getter<REQUEST> getGetter();

  protected abstract String url(REQUEST request);

  protected abstract String method(REQUEST request);

  @Nullable
  protected abstract String requestHeader(REQUEST request, String name);

  protected abstract int responseStatus(RESPONSE response);

  /**
   * Stores given context in the given request-response-loop storage in implementation specific way.
   */
  protected abstract void attachServerContext(Context context, STORAGE storage);

  /*
  We are making quite simple check by just verifying the presence of schema.
   */
  protected boolean isRelativeUrl(String url) {
    return !(url.startsWith("http://") || url.startsWith("https://"));
  }
}
