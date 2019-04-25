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
public class PlayHttpServerDecorator extends HttpServerDecorator<Request, Request, Result> {
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
  protected URI url(final Request request) throws URISyntaxException {
    return new URI(request.secure() ? "https://" : "http://" + request.host() + request.uri());
  }

  @Override
  protected String peerHostname(final Request request) {
    return null;
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.remoteAddress();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return null;
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
