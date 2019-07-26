package datadog.trace.instrumentation.springwebflux.server;

import datadog.trace.api.DDTags;
import io.opentracing.Span;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

public class RouteOnSuccessOrError implements BiConsumer<HandlerFunction<?>, Throwable> {

  private static final Pattern SPECIAL_CHARACTERS_REGEX = Pattern.compile("[\\(\\)&|]");
  private static final Pattern SPACES_REGEX = Pattern.compile("[ \\t]+");

  private final RouterFunction routerFunction;
  private final ServerRequest serverRequest;

  public RouteOnSuccessOrError(
      final RouterFunction routerFunction, final ServerRequest serverRequest) {
    this.routerFunction = routerFunction;
    this.serverRequest = serverRequest;
  }

  @Override
  public void accept(final HandlerFunction<?> handler, final Throwable throwable) {
    if (handler != null) {
      final String predicateString = parsePredicateString();
      if (predicateString != null) {
        final Span span = (Span) serverRequest.attributes().get(AdviceUtils.SPAN_ATTRIBUTE);
        if (span != null) {
          span.setTag("request.predicate", predicateString);
        }
        final Span parentSpan =
            (Span) serverRequest.attributes().get(AdviceUtils.PARENT_SPAN_ATTRIBUTE);
        if (parentSpan != null) {
          parentSpan.setTag(DDTags.RESOURCE_NAME, parseResourceName(predicateString));
        }
      }
    }
  }

  private String parsePredicateString() {
    final String routerFunctionString = routerFunction.toString();
    // Router functions containing lambda predicates should not end up in span tags since they are
    // confusing
    if (routerFunctionString.startsWith(
        "org.springframework.web.reactive.function.server.RequestPredicates$$Lambda$")) {
      return null;
    } else {
      return routerFunctionString.replaceFirst("\\s*->.*$", "");
    }
  }

  private String parseResourceName(final String routerString) {
    return SPACES_REGEX
        .matcher(SPECIAL_CHARACTERS_REGEX.matcher(routerString).replaceAll(""))
        .replaceAll(" ")
        .trim();
  }
}
