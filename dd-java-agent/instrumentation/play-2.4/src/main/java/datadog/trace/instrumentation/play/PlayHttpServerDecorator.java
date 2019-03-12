package datadog.trace.instrumentation.play;

import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Request;
import play.api.mvc.Result;
import scala.Option;

@Slf4j
public class PlayHttpServerDecorator extends HttpServerDecorator<Request, Result> {
  public static final PlayHttpServerDecorator DECORATE = new PlayHttpServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"play"};
  }

  @Override
  protected String component() {
    return "play-action";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected String url(final Request request) {
    // FIXME: This code is similar to that from the netty integrations.
    try {
      URI uri = new URI(request.uri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && !request.host().isEmpty()) {
        uri = new URI("http://" + request.host() + request.uri());
      }
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toString();
    } catch (final URISyntaxException e) {
      log.debug("Cannot parse uri: {}", request.uri());
      return request.uri();
    }
  }

  @Override
  protected String hostname(final Request httpRequest) {
    return httpRequest.domain();
  }

  @Override
  protected Integer port(final Request httpRequest) {
    final String[] split = httpRequest.host().split(":");
    try {
      return split.length == 2 ? Integer.valueOf(split[1]) : null;
    } catch (final Exception e) {
      return null;
    }
  }

  @Override
  protected Integer status(final Result httpResponse) {
    return httpResponse.header().status();
  }

  @Override
  public Span onRequest(final Span span, final Request request) {
    super.onRequest(span, request);
    if (request != null) {
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md#router-tags-are-now-attributes
      final Option pathOption = request.tags().get("ROUTE_PATTERN");
      if (!pathOption.isEmpty()) {
        final String path = (String) pathOption.get();
        //        scope.span().setTag(Tags.HTTP_URL.getKey(), path);
        span.setTag(DDTags.RESOURCE_NAME, request.method() + " " + path);
      }
    }
    return span;
  }

  @Override
  public Span onError(final Span span, final Throwable throwable) {
    Tags.HTTP_STATUS.set(span, 500);
    return super.onError(span, throwable);
  }
}
