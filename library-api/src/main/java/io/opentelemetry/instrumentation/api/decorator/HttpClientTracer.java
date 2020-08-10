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

package io.opentelemetry.instrumentation.api.decorator;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.instrumentation.api.MoreAttributes;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpClientTracer<REQUEST, RESPONSE> extends ClientTracer {

  private static final Logger log = LoggerFactory.getLogger(HttpClientTracer.class);

  public static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected static final String USER_AGENT = "User-Agent";

  protected abstract String method(REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract Integer status(RESPONSE response);

  protected abstract String requestHeader(REQUEST request, String name);

  protected abstract String responseHeader(RESPONSE response, String name);

  protected abstract HttpTextFormat.Setter<REQUEST> getSetter();

  public Span startSpan(REQUEST request) {
    return startSpan(request, spanNameForRequest(request));
  }

  public Scope startScope(Span span, REQUEST request) {
    Context context = withSpan(span, Context.current());

    Setter<REQUEST> setter = getSetter();
    if (setter == null) {
      throw new IllegalStateException(
          "getSetter() not defined but calling startScope(), either getSetter must be implemented or the scope should be setup manually");
    }
    OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, setter);
    context = context.withValue(ClientTracer.CONTEXT_CLIENT_SPAN_KEY, span);
    return withScopedContext(context);
  }

  public void end(Span span, RESPONSE response) {
    onResponse(span, response);
    super.end(span);
  }

  public void endExceptionally(Span span, RESPONSE response, Throwable throwable) {
    onResponse(span, response);
    super.endExceptionally(span, throwable);
  }

  /**
   * Returns a new client {@link Span} if there is no client {@link Span} in the current {@link
   * Context}, or an invalid {@link Span} otherwise.
   */
  private Span startSpan(REQUEST request, String name) {
    Context context = Context.current();
    Span clientSpan = ClientTracer.CONTEXT_CLIENT_SPAN_KEY.get(context);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return DefaultSpan.getInvalid();
    }

    Span current = TracingContextUtils.getSpan(context);
    Span span = tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(current).startSpan();
    onRequest(span, request);
    return span;
  }

  protected Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), method(request));

      String userAgent = requestHeader(request, USER_AGENT);
      if (userAgent != null) {
        SemanticAttributes.HTTP_USER_AGENT.set(span, userAgent);
      }

      // Copy of HttpServerDecorator url handling
      try {
        URI url = url(request);
        if (url != null) {
          StringBuilder urlBuilder = new StringBuilder();
          if (url.getScheme() != null) {
            urlBuilder.append(url.getScheme());
            urlBuilder.append("://");
          }
          if (url.getHost() != null) {
            urlBuilder.append(url.getHost());
            setPeer(span, url.getHost(), null);
            if (url.getPort() > 0) {
              span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), url.getPort());
              if (url.getPort() != 80 && url.getPort() != 443) {
                urlBuilder.append(":");
                urlBuilder.append(url.getPort());
              }
            }
          }
          String path = url.getPath();
          if (path.isEmpty()) {
            urlBuilder.append("/");
          } else {
            urlBuilder.append(path);
          }
          String query = url.getQuery();
          if (query != null) {
            urlBuilder.append("?").append(query);
          }
          String fragment = url.getFragment();
          if (fragment != null) {
            urlBuilder.append("#").append(fragment);
          }

          span.setAttribute(SemanticAttributes.HTTP_URL.key(), urlBuilder.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            span.setAttribute(MoreAttributes.HTTP_QUERY, query);
            span.setAttribute(MoreAttributes.HTTP_FRAGMENT, fragment);
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }
    }
    return span;
  }

  protected Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      Integer status = status(response);
      if (status != null) {
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.key(), status);
        span.setStatus(HttpStatusConverter.statusFromHttpStatus(status));
      }
    }
    return span;
  }

  protected String spanNameForRequest(final REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }
}
