package datadog.trace.instrumentation.play26;

import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import play.api.mvc.Request;
import play.api.mvc.Result;
import play.api.routing.HandlerDef;
import play.routing.Router;
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
    return new URI((request.secure() ? "https://" : "http://") + request.host() + request.uri());
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
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md
      final Option<HandlerDef> defOption =
          request.attrs().get(Router.Attrs.HANDLER_DEF.underlying());
      if (!defOption.isEmpty()) {
        final String path = defOption.get().path();
        span.setTag(DDTags.RESOURCE_NAME, request.method() + " " + path);
      }
    }
    return span;
  }

  @Override
  public Span onError(final Span span, Throwable throwable) {
    Tags.HTTP_STATUS.set(span, 500);
    if (throwable != null
        // This can be moved to instanceof check when using Java 8.
        && throwable.getClass().getName().equals("java.util.concurrent.CompletionException")
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    while ((throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return super.onError(span, throwable);
  }
}
