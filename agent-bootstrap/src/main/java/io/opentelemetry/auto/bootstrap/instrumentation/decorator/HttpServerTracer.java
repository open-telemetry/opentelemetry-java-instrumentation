/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import static io.opentelemetry.OpenTelemetry.getPropagators;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

// TODO In search for a better home package
@Slf4j
public abstract class HttpServerTracer<REQUEST> {
  public static final String CONTEXT_ATTRIBUTE = "io.opentelemetry.instrumentation.context";

  protected final Tracer tracer;

  public HttpServerTracer() {
    tracer = OpenTelemetry.getTracerProvider().get(getInstrumentationName(), getVersion());
  }

  protected abstract String getInstrumentationName();

  protected abstract String getVersion();

  public Span startSpan(REQUEST request, Method origin, String originType) {
    final Span.Builder builder =
        tracer
            .spanBuilder(spanNameForMethod(origin))
            .setSpanKind(SERVER)
            .setParent(extract(request, getGetter()))
            // TODO Where span.origin.type is defined?
            .setAttribute("span.origin.type", originType);

    Span span = builder.startSpan();
    onConnection(span, request);
    onRequest(span, request);

    return span;
  }

  protected void onConnection(Span span, REQUEST request) {
    SemanticAttributes.NET_PEER_IP.set(span, peerHostIP(request));
    final Integer port = peerPort(request);
    // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
    if (port != null && port > 0) {
      SemanticAttributes.NET_PEER_PORT.set(span, port);
    }
  }

  // TODO use semantic attributes
  protected void onRequest(final Span span, final REQUEST request) {
    SemanticAttributes.HTTP_METHOD.set(span, method(request));

    // Copy of HttpClientDecorator url handling
    try {
      final URI url = url(request);
      if (url != null) {
        final StringBuilder urlBuilder = new StringBuilder();
        if (url.getScheme() != null) {
          urlBuilder.append(url.getScheme());
          urlBuilder.append("://");
        }
        if (url.getHost() != null) {
          urlBuilder.append(url.getHost());
          if (url.getPort() > 0 && url.getPort() != 80 && url.getPort() != 443) {
            urlBuilder.append(":");
            urlBuilder.append(url.getPort());
          }
        }
        final String path = url.getPath();
        if (path.isEmpty()) {
          urlBuilder.append("/");
        } else {
          urlBuilder.append(path);
        }
        final String query = url.getQuery();
        if (query != null) {
          urlBuilder.append("?").append(query);
        }
        final String fragment = url.getFragment();
        if (fragment != null) {
          urlBuilder.append("#").append(fragment);
        }

        span.setAttribute(Tags.HTTP_URL, urlBuilder.toString());

        if (Config.get().isHttpServerTagQueryString()) {
          span.setAttribute(MoreTags.HTTP_QUERY, url.getQuery());
          span.setAttribute(MoreTags.HTTP_FRAGMENT, url.getFragment());
        }
      }
    } catch (final Exception e) {
      log.debug("Error tagging url", e);
    }
    // TODO set resource name from URL.
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  protected String spanNameForMethod(final Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForClass(final Class clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      final String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = clazz.getName().replace(pkgName, "").substring(1);
      }
    }
    return className;
  }

  protected void onError(final Span span, final Throwable throwable) {
    addThrowable(span, unwrapThrowable(throwable));
  }

  // TODO semantic attributes
  public static void addThrowable(final Span span, final Throwable throwable) {
    span.setAttribute(MoreTags.ERROR_MSG, throwable.getMessage());
    span.setAttribute(MoreTags.ERROR_TYPE, throwable.getClass().getName());

    final StringWriter errorString = new StringWriter();
    throwable.printStackTrace(new PrintWriter(errorString));
    span.setAttribute(MoreTags.ERROR_STACK, errorString.toString());
  }

  public Span getCurrentSpan() {
    return tracer.getCurrentSpan();
  }

  /**
   * Creates new scoped context with the given span.
   *
   * <p>Attaches new context to the request to avoid creating duplicate server spans.
   */
  public Scope startScope(Span span, REQUEST request) {
    Context newContext = withSpan(span, Context.current());
    attachContextToRequest(newContext, request);
    return withScopedContext(newContext);
  }

  // TODO should end methods remove SPAN attribute from request as well?
  public void end(Span span, int responseStatus) {
    setStatus(span, responseStatus);
    span.end();
  }

  public void endExceptionally(Span span, Throwable throwable, int responseStatus) {
    if (responseStatus == 200) {
      // TODO I think this is wrong.
      // We must report that response status that was actually sent to end user
      // We may change span status, but not http_status attribute
      responseStatus = 500;
    }
    onError(span, unwrapThrowable(throwable));
    end(span, responseStatus);
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    return throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  private <C> SpanContext extract(final C carrier, final HttpTextFormat.Getter<C> getter) {
    final Context context =
        getPropagators().getHttpTextFormat().extract(Context.current(), carrier, getter);
    final Span span = getSpan(context);
    return span.getContext();
  }

  private void setStatus(Span span, int status) {
    SemanticAttributes.HTTP_STATUS_CODE.set(span, status);
    // TODO status_message
    Status otelStatus = HttpUtil.statusFromHttpStatus(status);
    if (!otelStatus.isOk()) {
      span.setStatus(otelStatus);
    }
  }

  protected abstract Integer peerPort(REQUEST request);

  protected abstract String peerHostIP(REQUEST request);

  protected abstract HttpTextFormat.Getter<REQUEST> getGetter();

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract String method(REQUEST request);

  /** Stores given context in the given request in implementation specific way. */
  protected abstract void attachContextToRequest(Context context, REQUEST request);

  /**
   * Returns context stored to given request by {@link #attachContextToRequest(Context, REQUEST)}.
   *
   * <p>May be null.
   */
  public abstract Context getAttachedContext(REQUEST request);
}
