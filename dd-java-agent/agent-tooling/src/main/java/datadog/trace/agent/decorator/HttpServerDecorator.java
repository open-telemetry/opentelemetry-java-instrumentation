package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpServerDecorator<REQUEST, CONNECTION, RESPONSE> extends ServerDecorator {
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
    return DDSpanTypes.HTTP_SERVER;
  }

  @Override
  protected boolean traceAnalyticsDefault() {
    return Config.getBooleanSettingFromEnvironment(Config.TRACE_ANALYTICS_ENABLED, false);
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      Tags.HTTP_METHOD.set(span, method(request));

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

          Tags.HTTP_URL.set(span, urlNoParams.toString());

          if (Config.get().isHttpServerTagQueryString()) {
            span.setTag("http.query.string", url.getQuery());
            span.setTag("http.fragment.string", url.getFragment());
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
      Tags.PEER_HOSTNAME.set(span, peerHostname(connection));
      final String ip = peerHostIP(connection);
      if (ip != null) {
        if (VALID_IPV4_ADDRESS.matcher(ip).matches()) {
          Tags.PEER_HOST_IPV4.set(span, ip);
        } else if (ip.contains(":")) {
          Tags.PEER_HOST_IPV6.set(span, ip);
        }
      }
      final Integer port = peerPort(connection);
      // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
      Tags.PEER_PORT.set(span, port != null && port > 0 ? port : null);
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        Tags.HTTP_STATUS.set(span, status);

        if (Config.get().getHttpServerErrorStatuses().contains(status)) {
          Tags.ERROR.set(span, true);
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
