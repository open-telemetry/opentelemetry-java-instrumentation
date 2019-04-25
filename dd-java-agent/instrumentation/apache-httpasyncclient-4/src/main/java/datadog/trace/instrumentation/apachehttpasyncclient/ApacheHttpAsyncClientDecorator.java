package datadog.trace.instrumentation.apachehttpasyncclient;

import datadog.trace.agent.decorator.HttpClientDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class ApacheHttpAsyncClientDecorator extends HttpClientDecorator<HttpRequest, HttpContext> {
  public static final ApacheHttpAsyncClientDecorator DECORATE =
      new ApacheHttpAsyncClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"httpasyncclient", "apache-httpasyncclient"};
  }

  @Override
  protected String component() {
    return "apache-httpasyncclient";
  }

  @Override
  protected String method(final HttpRequest request) {
    final RequestLine requestLine = request.getRequestLine();
    return requestLine == null ? null : requestLine.getMethod();
  }

  @Override
  protected URI url(final HttpRequest request) throws URISyntaxException {
    final RequestLine requestLine = request.getRequestLine();
    return requestLine == null ? null : new URI(requestLine.getUri());
  }

  @Override
  protected String hostname(final HttpRequest request) {
    final RequestLine requestLine = request.getRequestLine();
    if (requestLine != null) {
      try {
        return new URI(requestLine.getUri()).getHost();
      } catch (final URISyntaxException e) {
        return null;
      }
    }
    return null;
  }

  @Override
  protected Integer port(final HttpRequest request) {
    final RequestLine requestLine = request.getRequestLine();
    if (requestLine != null) {
      try {
        return new URI(requestLine.getUri()).getPort();
      } catch (final URISyntaxException e) {
        return null;
      }
    }
    return null;
  }

  @Override
  protected Integer status(final HttpContext context) {
    final Object responseObject = context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
    if (responseObject instanceof HttpResponse) {
      final StatusLine statusLine = ((HttpResponse) responseObject).getStatusLine();
      if (statusLine != null) {
        return statusLine.getStatusCode();
      }
    }
    return null;
  }
}
