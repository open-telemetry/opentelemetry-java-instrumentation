/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

// TODO In search for a better home package

/**
 * Base class for implementing Tracers for HTTP servers. It has 3 types that must be specified by
 * subclasses:
 *
 * @param <REQUEST> - The specific type for HTTP requests
 * @param <RESPONSE> - The specific type for HTTP responses
 * @param <CONNECTION> - The specific type of HTTP connection, used to get peer address information
 *     and HTTP flavor.
 * @param <STORAGE> - Implementation specific storage type for attaching/getting the server context.
 *     Use Void if your subclass does not have an implementation specific storage need.
 */
public abstract class HttpServerTracer<REQUEST, RESPONSE, CONNECTION, STORAGE> extends BaseTracer {

  // the class name is part of the attribute name, so that it will be shaded when used in javaagent
  // instrumentation, and won't conflict with usage outside javaagent instrumentation
  public static final String CONTEXT_ATTRIBUTE = HttpServerTracer.class.getName() + ".Context";

  protected static final String USER_AGENT = "User-Agent";

  protected HttpServerTracer() {
    super();
  }

  protected HttpServerTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public Context startSpan(REQUEST request, CONNECTION connection, STORAGE storage, Method origin) {
    String spanName = SpanNames.fromMethod(origin);
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
    SpanBuilder spanBuilder = spanBuilder(parentContext, spanName, SERVER);

    if (startTimestamp >= 0) {
      spanBuilder.setStartTimestamp(startTimestamp, TimeUnit.NANOSECONDS);
    }

    onConnection(spanBuilder, connection);
    onRequest(spanBuilder, request);
    onConnectionAndRequest(spanBuilder, connection, request);

    Context context = withServerSpan(parentContext, spanBuilder.startSpan());
    context = customizeContext(context, request);
    attachServerContext(context, storage);

    return context;
  }

  /** Override in subclass to customize context that is returned by {@code startSpan}. */
  protected Context customizeContext(Context context, REQUEST request) {
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
    end(context, timestamp);
  }

  /**
   * Convenience method. Delegates to {@link #endExceptionally(Context, Throwable, Object)}, passing
   * {@code response} value of {@code null}.
   */
  @Override
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
    onException(context, throwable);
    Span span = Span.fromContext(context);
    if (response == null) {
      setStatus(span, 500);
    } else {
      setStatus(span, responseStatus(response));
    }
    end(context, timestamp);
  }

  public Span getServerSpan(STORAGE storage) {
    Context attachedContext = getServerContext(storage);
    return attachedContext == null ? null : ServerSpan.fromContextOrNull(attachedContext);
  }

  /**
   * Returns context stored to the given request-response-loop storage by {@link
   * #attachServerContext(Context, Object)}.
   */
  @Nullable
  public abstract Context getServerContext(STORAGE storage);

  protected void onConnection(SpanBuilder spanBuilder, CONNECTION connection) {
    // TODO: use NetPeerAttributes here
    spanBuilder.setAttribute(SemanticAttributes.NET_PEER_IP, peerHostIp(connection));
    Integer port = peerPort(connection);
    // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
    if (port != null && port > 0) {
      spanBuilder.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) port);
    }
  }

  protected void onRequest(SpanBuilder spanBuilder, REQUEST request) {
    spanBuilder.setAttribute(SemanticAttributes.HTTP_METHOD, method(request));
    spanBuilder.setAttribute(
        SemanticAttributes.HTTP_USER_AGENT, requestHeader(request, USER_AGENT));

    String url = url(request);
    if (url != null) {
      // netty instrumentation uses this
      spanBuilder.setAttribute(SemanticAttributes.HTTP_URL, url);
    } else {
      spanBuilder.setAttribute(SemanticAttributes.HTTP_SCHEME, scheme(request));
      spanBuilder.setAttribute(SemanticAttributes.HTTP_HOST, host(request));
      spanBuilder.setAttribute(SemanticAttributes.HTTP_TARGET, target(request));
    }
  }

  protected void onConnectionAndRequest(
      SpanBuilder spanBuilder, CONNECTION connection, REQUEST request) {
    String flavor = flavor(connection, request);
    if (flavor != null) {
      // remove HTTP/ prefix to comply with semantic conventions
      if (flavor.startsWith("HTTP/")) {
        flavor = flavor.substring("HTTP/".length());
      }
      spanBuilder.setAttribute(SemanticAttributes.HTTP_FLAVOR, flavor);
    }
    spanBuilder.setAttribute(SemanticAttributes.HTTP_CLIENT_IP, clientIp(request));
  }

  private String clientIp(REQUEST request) {
    // try Forwarded
    String forwarded = requestHeader(request, "Forwarded");
    if (forwarded != null) {
      forwarded = extractForwarded(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-For
    forwarded = requestHeader(request, "X-Forwarded-For");
    if (forwarded != null) {
      forwarded = extractForwardedFor(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    return null;
  }

  // VisibleForTesting
  static String extractForwarded(String forwarded) {
    int start = forwarded.toLowerCase().indexOf("for=");
    if (start < 0) {
      return null;
    }
    start += 4; // start is now the index after for=
    if (start >= forwarded.length() - 1) { // the value after for= must not be empty
      return null;
    }
    return extractIpAddress(forwarded, start);
  }

  // VisibleForTesting
  static String extractForwardedFor(String forwarded) {
    return extractIpAddress(forwarded, 0);
  }

  // from https://www.rfc-editor.org/rfc/rfc7239
  //  "Note that IPv6 addresses may not be quoted in
  //   X-Forwarded-For and may not be enclosed by square brackets, but they
  //   are quoted and enclosed in square brackets in Forwarded"
  // and also (applying to Forwarded but not X-Forwarded-For)
  //  "It is important to note that an IPv6 address and any nodename with
  //   node-port specified MUST be quoted, since ':' is not an allowed
  //   character in 'token'."
  private static String extractIpAddress(String forwarded, int start) {
    if (forwarded.length() == start) {
      return null;
    }
    if (forwarded.charAt(start) == '"') {
      return extractIpAddress(forwarded, start + 1);
    }
    if (forwarded.charAt(start) == '[') {
      int end = forwarded.indexOf(']', start + 1);
      if (end == -1) {
        return null;
      }
      return forwarded.substring(start + 1, end);
    }
    boolean inIpv4 = false;
    for (int i = start; i < forwarded.length() - 1; i++) {
      char c = forwarded.charAt(i);
      if (c == '.') {
        inIpv4 = true;
      } else if (c == ',' || c == ';' || c == '"' || (inIpv4 && c == ':')) {
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
    StatusCode statusCode = HttpStatusConverter.serverStatusFromHttpStatus(status);
    if (statusCode != StatusCode.UNSET) {
      span.setStatus(statusCode);
    }
  }

  @Nullable
  protected abstract Integer peerPort(CONNECTION connection);

  @Nullable
  protected abstract String peerHostIp(CONNECTION connection);

  protected abstract String flavor(CONNECTION connection, REQUEST request);

  protected abstract TextMapGetter<REQUEST> getGetter();

  // netty still uses this, otherwise should prefer scheme/host/target
  @Nullable
  protected String url(REQUEST request) {
    return null;
  }

  protected abstract String scheme(REQUEST request);

  protected abstract String host(REQUEST request);

  protected abstract String target(REQUEST request);

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
