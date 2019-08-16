package datadog.trace.instrumentation.grizzly;

import datadog.trace.agent.decorator.HttpServerDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

public class GrizzlyDecorator extends HttpServerDecorator<Request, Request, Response> {
  public static final GrizzlyDecorator DECORATE = new GrizzlyDecorator();

  @Override
  protected String method(final Request request) {
    return request.getMethod().getMethodString();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return new URI(request.getRequestURL().toString());
  }

  @Override
  protected String peerHostname(final Request request) {
    return request.getRemoteHost();
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return request.getRemotePort();
  }

  @Override
  protected Integer status(final Response containerResponse) {
    return containerResponse.getStatus();
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"grizzly"};
  }

  @Override
  protected String component() {
    return "grizzly";
  }
}
