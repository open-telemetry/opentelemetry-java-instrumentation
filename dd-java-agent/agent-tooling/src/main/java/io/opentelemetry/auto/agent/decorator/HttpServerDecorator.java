package io.opentelemetry.auto.agent.decorator;

import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpServerDecorator<REQUEST, CONNECTION, RESPONSE> extends ServerDecorator {
  public static final String SPAN_ATTRIBUTE = "io.opentelemetry.auto.span";

  // Source: https://www.regextester.com/22
  private static final Pattern VALID_IPV4_ADDRESS =
      Pattern.compile(
          "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");

  protected abstract String method(REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract String peerHostname(CONNECTION connection);

  protected abstract String peerHostIP(CONNECTION connection);

  protected abstract Integer peerPort(CONNECTION connection);

  protected abstract Integer status(RESPONSE response);

  @Override
  protected String spanType() {
    return SpanTypes.HTTP_SERVER;
  }

  public AgentSpan onRequest(final AgentSpan span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setTag(Tags.HTTP_METHOD, method(request));

      // Copy of HttpClientDecorator url handling
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

          if (Config.get().isHttpServerTagQueryString()) {
            span.setTag(MoreTags.HTTP_QUERY, url.getQuery());
            span.setTag(MoreTags.HTTP_FRAGMENT, url.getFragment());
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }
      // TODO set resource name from URL.
    }
    return span;
  }

  public AgentSpan onConnection(final AgentSpan span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setTag(Tags.PEER_HOSTNAME, peerHostname(connection));
      final String ip = peerHostIP(connection);
      if (ip != null) {
        if (VALID_IPV4_ADDRESS.matcher(ip).matches()) {
          span.setTag(Tags.PEER_HOST_IPV4, ip);
        } else if (ip.contains(":")) {
          span.setTag(Tags.PEER_HOST_IPV6, ip);
        }
      }
      final Integer port = peerPort(connection);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      if (port != null && port > 0) {
        span.setTag(Tags.PEER_PORT, port);
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

        if (Config.get().getHttpServerErrorStatuses().contains(status)) {
          span.setError(true);
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
  //      span.setTag("http.status", 500);
  //    }
  //    return super.onError(span, throwable);
  //  }
}
