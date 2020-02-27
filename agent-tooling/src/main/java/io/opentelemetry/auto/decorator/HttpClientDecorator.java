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
package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpClientDecorator<REQUEST, RESPONSE> extends ClientDecorator {

  protected abstract String method(REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract String hostname(REQUEST request);

  protected abstract Integer port(REQUEST request);

  protected abstract Integer status(RESPONSE response);

  @Override
  protected String getSpanType() {
    return SpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String service() {
    return null;
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      final String method = method(request);
      if (method != null) {
        span.setAttribute(Tags.HTTP_METHOD, method);
      }

      // Copy of HttpServerDecorator url handling
      try {
        final URI url = url(request);
        if (url != null) {
          final StringBuilder urlNoParams = new StringBuilder();
          if (url.getScheme() != null) {
            urlNoParams.append(url.getScheme());
            urlNoParams.append("://");
          }
          if (url.getHost() != null) {
            urlNoParams.append(url.getHost());
            if (url.getPort() > 0 && url.getPort() != 80 && url.getPort() != 443) {
              urlNoParams.append(":");
              urlNoParams.append(url.getPort());
            }
          }
          final String path = url.getPath();
          if (path.isEmpty()) {
            urlNoParams.append("/");
          } else {
            urlNoParams.append(path);
          }

          span.setAttribute(Tags.HTTP_URL, urlNoParams.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            final String query = url.getQuery();
            if (query != null) {
              span.setAttribute(MoreTags.HTTP_QUERY, query);
            }
            final String fragment = url.getFragment();
            if (fragment != null) {
              span.setAttribute(MoreTags.HTTP_FRAGMENT, fragment);
            }
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }

      final String hostname = hostname(request);
      if (hostname != null) {
        span.setAttribute(Tags.PEER_HOSTNAME, hostname);

        if (Config.get().isHttpClientSplitByDomain()) {
          span.setAttribute(MoreTags.SERVICE_NAME, hostname);
        }
      }
      final Integer port = port(request);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      if (port != null && port > 0) {
        span.setAttribute(Tags.PEER_PORT, port);
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

        if (Config.get().getHttpClientErrorStatuses().contains(status)) {
          span.setStatus(Status.UNKNOWN);
        }
      }
    }
    return span;
  }
}
