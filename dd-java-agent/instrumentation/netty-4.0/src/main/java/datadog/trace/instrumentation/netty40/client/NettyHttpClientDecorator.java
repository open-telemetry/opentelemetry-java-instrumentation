package datadog.trace.instrumentation.netty40.client;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;

import datadog.trace.agent.decorator.HttpClientDecorator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {
  public static final NettyHttpClientDecorator DECORATE = new NettyHttpClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"netty", "netty-4.0"};
  }

  @Override
  protected String component() {
    return "netty-client";
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected String url(final HttpRequest request) {
    // FIXME: This code is duplicated across netty integrations.
    try {
      URI uri = new URI(request.getUri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        uri = new URI("http://" + request.headers().get(HOST) + request.getUri());
      }
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toString();
    } catch (final URISyntaxException e) {
      log.debug("Cannot parse netty uri: {}", request.getUri());
      return request.getUri();
    }
  }

  @Override
  protected String hostname(final HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected Integer port(final HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }
}
