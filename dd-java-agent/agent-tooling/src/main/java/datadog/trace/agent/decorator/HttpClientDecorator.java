package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
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

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      Tags.HTTP_METHOD.set(span, method(request));

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

          Tags.HTTP_URL.set(span, urlNoParams.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            span.setTag(DDTags.HTTP_QUERY, url.getQuery());
            span.setTag(DDTags.HTTP_FRAGMENT, url.getFragment());
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }

      Tags.PEER_HOSTNAME.set(span, hostname(request));
      final Integer port = port(request);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      Tags.PEER_PORT.set(span, port != null && port > 0 ? port : null);

      if (Config.get().isHttpClientSplitByDomain()) {
        span.setTag(DDTags.SERVICE_NAME, hostname(request));
      }
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        Tags.HTTP_STATUS.set(span, status);

        if (Config.get().getHttpClientErrorStatuses().contains(status)) {
          Tags.ERROR.set(span, true);
        }
      }
    }
    return span;
  }
}
