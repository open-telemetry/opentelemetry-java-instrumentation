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
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
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

@Slf4j
public abstract class HttpServerTracer<REQUEST, RESPONSE> {
  public static final String SPAN_ATTRIBUTE = "io.opentelemetry.auto.span";

  protected static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected final Tracer tracer;

  public HttpServerTracer() {
    tracer = OpenTelemetry.getTracerProvider().get(getInstrumentationName(), getVersion());
  }

  protected abstract String getVersion();

  protected abstract String getInstrumentationName();

  protected void onConnection(REQUEST request, Span span) {
    SemanticAttributes.NET_PEER_IP.set(span, peerHostIP(request));
    final Integer port = peerPort(request);
    // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
    if (port != null && port > 0) {
      SemanticAttributes.NET_PEER_PORT.set(span, port);
    }
  }

  public SpanWithScope startSpan(REQUEST request, String originType) {
    final Span existingSpan = findExistingSpan(request);
    if (existingSpan != null) {
      /*
      Given request already has a span associated with it.
      As there should not be nested spans of kind SERVER, we should NOT create a new span here.

      But it may happen that there is no span in current Context or it is from a different trace.
      E.g. in case of async servlet request processing we create span for incoming request in one thread,
      but actual request continues processing happens in another thread.
      Depending on servlet container implementation, this processing may again arrive into this method.
      E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

      In this case we have to put the span from the request into current context before continuing.
      */
      final boolean spanContextWasLost = !sameTrace(tracer.getCurrentSpan(), existingSpan);
      if (spanContextWasLost) {
        //Put span from request attribute into current context.
        //We did not create a new span here, so return null instead
        return new SpanWithScope(null, currentContextWith(existingSpan));
      } else {
        //We are inside nested servlet/filter, don't create new span
        return null;
      }
    }

    final Span.Builder builder = tracer.spanBuilder(getSpanName(request))
        .setSpanKind(SERVER)
        .setParent(extract(request, getGetter()))
        //TODO Where span.origin.type is defined?
        .setAttribute("span.origin.type", originType);

    Span span = builder.startSpan();
    //TODO fix parameter order
    onConnection(request, span);
    onRequest(span, request);

    return new SpanWithScope(span, currentContextWith(span));
  }

  protected Span findExistingSpan(REQUEST request) {
    return null;
  }

  private boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan.getContext().getTraceId().equals(otherSpan.getContext().getTraceId());
  }

  protected abstract Integer peerPort(REQUEST request);

  protected abstract String peerHostIP(REQUEST request);

  //TODO use semantic attributes
  public void onRequest(final Span span, final REQUEST request) {
    persistSpanToRequest(span, request);
    span.setAttribute(Tags.HTTP_METHOD, method(request));

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

  protected abstract void persistSpanToRequest(Span span, REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  private String getSpanName(REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    final String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }

  protected abstract String method(REQUEST request);

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForMethod(final Method method) {
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

  //TODO semantic attributes
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

  protected abstract HttpTextFormat.Getter<REQUEST> getGetter();

  //TODO should end methods remove SPAN attribute from request as well?
  public void end(SpanWithScope spanWithScope, RESPONSE response){
    if (spanWithScope == null) {
      return;
    }

    final Span span = spanWithScope.getSpan();
    if (span != null) {
      end(span, response);
    }

    spanWithScope.closeScope();
  }

  public void end(Span span, RESPONSE response){
    int responseStatus = status(response);
    setStatus(span, responseStatus);
    span.end();
  }

  public void endExceptionally(SpanWithScope spanWithScope, Throwable throwable, RESPONSE response){
    if (spanWithScope == null) {
      return;
    }

    final Span span = spanWithScope.getSpan();
    if (span != null) {
      endExceptionally(span, throwable, response);
    }

    spanWithScope.closeScope();
  }

  public void endExceptionally(Span span, Throwable throwable, RESPONSE response){
    int responseStatus = status(response);
    if (responseStatus == 200) {
      //TODO I think this is wrong.
      //We must report that response status that was actually sent to end user
      //We may change span status, but not http_status attribute
      responseStatus = 500;
    }
    setStatus(span, responseStatus);
    onError(span, unwrapThrowable(throwable));
    span.end();
  }

  protected abstract int status(RESPONSE response);

  protected Throwable unwrapThrowable(Throwable throwable) {
    return throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  public static <C> SpanContext extract(final C carrier, final HttpTextFormat.Getter<C> getter) {
    final Context context =
        getPropagators().getHttpTextFormat().extract(Context.current(), carrier, getter);
    final Span span = getSpan(context);
    return span.getContext();
  }

  private void setStatus(Span span, int status) {
    SemanticAttributes.HTTP_STATUS_CODE.set(span, status);
    //TODO status_message
    if (Config.get().getHttpServerErrorStatuses().contains(status)) {
      span.setStatus(Status.UNKNOWN);
    }
  }
}
