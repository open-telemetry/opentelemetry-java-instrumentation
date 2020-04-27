package datadog.trace.instrumentation.ratpack;

import com.google.common.net.HostAndPort;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import ratpack.handling.Context;
import ratpack.http.HttpUrlBuilder;
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
  protected boolean traceAnalyticsDefault() {
    return false;
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
    final HttpUrlBuilder url =
        publicAddress.builder().path(request.getPath()).params(request.getQueryParams());
    return url.build();
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.getRemoteAddress().getHost();
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

  public AgentSpan onContext(final AgentSpan span, final Context ctx) {

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

  @Override
  public AgentSpan onError(final AgentSpan span, final Throwable throwable) {
    // Attempt to unwrap ratpack.handling.internal.HandlerException without direct reference.
    if (throwable instanceof Error && throwable.getCause() != null) {
      return super.onError(span, throwable.getCause());
    }
    return super.onError(span, throwable);
  }
}
