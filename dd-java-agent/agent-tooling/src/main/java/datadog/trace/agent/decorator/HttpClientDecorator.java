package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.opentracing.tag.Tags;
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
    return DDSpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String service() {
    return null;
  }

  public AgentSpan onRequest(final AgentSpan span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setTag(Tags.HTTP_METHOD.getKey(), method(request));

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

          span.setTag(Tags.HTTP_URL.getKey(), urlNoParams.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            span.setTag(DDTags.HTTP_QUERY, url.getQuery());
            span.setTag(DDTags.HTTP_FRAGMENT, url.getFragment());
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }

      span.setTag(Tags.PEER_HOSTNAME.getKey(), hostname(request));
      final Integer port = port(request);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      if (port != null && port > 0) {
        span.setTag(Tags.PEER_PORT.getKey(), port);
      }

      if (Config.get().isHttpClientSplitByDomain()) {
        span.setTag(DDTags.SERVICE_NAME, hostname(request));
      }
    }
    return span;
  }

  public AgentSpan onResponse(final AgentSpan span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        span.setTag(Tags.HTTP_STATUS.getKey(), status);

        if (Config.get().getHttpClientErrorStatuses().contains(status)) {
          span.setError(true);
        }
      }
    }
    return span;
  }
}
