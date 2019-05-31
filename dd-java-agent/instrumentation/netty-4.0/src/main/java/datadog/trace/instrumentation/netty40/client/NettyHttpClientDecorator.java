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
  protected URI url(final HttpRequest request) throws URISyntaxException {
    final URI uri = new URI(request.getUri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.getUri());
    } else {
      return uri;
    }
  }

  @Override
  protected String hostname(final HttpRequest request) {
    try {
      final URI uri = new URI(request.getUri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        return request.headers().get(HOST).split(":")[0];
      } else {
        return uri.getHost();
      }
    } catch (final Exception e) {
      return null;
    }
  }

  @Override
  protected Integer port(final HttpRequest request) {
    try {
      final URI uri = new URI(request.getUri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        final String[] hostPort = request.headers().get(HOST).split(":");
        return hostPort.length == 2 ? Integer.parseInt(hostPort[1]) : null;
      } else {
        return uri.getPort();
      }
    } catch (final Exception e) {
      return null;
    }
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatus().code();
  }
}
