package io.opentelemetry.auto.agent.decorator;

import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
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
  protected String spanType() {
    return SpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String service() {
    return null;
  }

  public AgentSpan onRequest(final AgentSpan span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setTag(Tags.HTTP_METHOD, method(request));

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

          span.setTag(Tags.HTTP_URL, urlNoParams.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            span.setTag(MoreTags.HTTP_QUERY, url.getQuery());
            span.setTag(MoreTags.HTTP_FRAGMENT, url.getFragment());
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }

      span.setTag(Tags.PEER_HOSTNAME, hostname(request));
      final Integer port = port(request);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      if (port != null && port > 0) {
        span.setTag(Tags.PEER_PORT, port);
      }

      if (Config.get().isHttpClientSplitByDomain()) {
        span.setTag(MoreTags.SERVICE_NAME, hostname(request));
      }
    }
    return span;
  }

  public AgentSpan onResponse(final AgentSpan span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        span.setTag(Tags.HTTP_STATUS, status);

        if (Config.get().getHttpClientErrorStatuses().contains(status)) {
          span.setError(true);
        }
      }
    }
    return span;
  }
}
