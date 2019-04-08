package datadog.trace.instrumentation.ratpack;

import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.Status;

public class RatpackServerDecorator extends HttpServerDecorator<Request, Response> {
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
  protected String url(final Request request) {
    return request.getUri();
  }

  @Override
  protected String hostname(final Request request) {
    return null;
  }

  @Override
  protected Integer port(final Request request) {
    return null;
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
      description = ctx.getRequest().getUri();
    }
    if (!description.startsWith("/")) {
      description = "/" + description;
    }

    final String resourceName = ctx.getRequest().getMethod().getName() + " " + description;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    return span;
  }
}
