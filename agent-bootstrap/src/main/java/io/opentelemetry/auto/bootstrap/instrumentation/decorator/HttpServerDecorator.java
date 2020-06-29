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

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

/** @deprecated use {@link HttpServerTracer} instead. */
@Deprecated
@Slf4j
public abstract class HttpServerDecorator<REQUEST, CONNECTION, RESPONSE> extends ServerDecorator {
  public static final String RESPONSE_ATTRIBUTE = "io.opentelemetry.auto.response";
  public static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected abstract String method(REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract String peerHostIP(CONNECTION connection);

  protected abstract Integer peerPort(CONNECTION connection);

  protected abstract Integer status(RESPONSE response);

  public String spanNameForRequest(final REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    final String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
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
    return span;
  }

  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setAttribute(MoreTags.NET_PEER_IP, peerHostIP(connection));
      final Integer port = peerPort(connection);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      if (port != null && port > 0) {
        span.setAttribute(MoreTags.NET_PEER_PORT, port);
      }
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        span.setAttribute(Tags.HTTP_STATUS, status);

        if (Config.get().getHttpServerErrorStatuses().get(status)) {
          span.setStatus(HttpUtil.statusFromHttpStatus(status));
        }
      }
    }
    return span;
  }

  //  @Override
  //  public Span onError(final Span span, final Throwable throwable) {
  //    assert span != null;
  //    // FIXME
  //    final Object status = span.getTag("http.status");
  //    if (status == null || status.equals(200)) {
  //      // Ensure status set correctly
  //      span.setAttribute("http.status", 500);
  //    }
  //    return super.onError(span, throwable);
  //  }
}
