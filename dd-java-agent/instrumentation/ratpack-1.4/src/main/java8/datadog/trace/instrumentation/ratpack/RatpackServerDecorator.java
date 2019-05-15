package datadog.trace.instrumentation.ratpack;

import com.google.common.net.HostAndPort;
import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.Status;
import ratpack.server.PublicAddress;

@Slf4j
public class RatpackServerDecorator extends HttpServerDecorator<Request, Request, Response> {
  public static final RatpackServerDecorator DECORATE = new RatpackServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"ratpack"};
  }

  @Override
  protected String component() {
    return "ratpack";
  }

  @Override
  protected String method(final Request request) {
    return request.getMethod().getName();
  }

  @Override
  protected URI url(final Request request) {
    final HostAndPort address = request.getLocalAddress();
    // This call implicitly uses request via a threadlocal provided by ratpack.
    final PublicAddress publicAddress =
        PublicAddress.inferred(address.getPort() == 443 ? "https" : "http");
    return publicAddress.get(request.getPath());
  }

  @Override
  protected String peerHostname(final Request request) {
    return request.getRemoteAddress().getHostText();
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.getRemoteAddress().getHostText();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return request.getRemoteAddress().getPort();
  }

  @Override
  protected Integer status(final Response response) {
    final Status status = response.getStatus();
    if (status != null) {
      return status.getCode();
    } else {
      return null;
    }
  }

  public Span onContext(final Span span, final Context ctx) {

    String description = ctx.getPathBinding().getDescription();
    if (description == null || description.isEmpty()) {
      description = "/";
    } else if (!description.startsWith("/")) {
      description = "/" + description;
    }

    final String resourceName = ctx.getRequest().getMethod().getName() + " " + description;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    return span;
  }
}
