/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.spring.webflux.SpringWebfluxConfig;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

public class RouteOnSuccessOrError implements BiConsumer<HandlerFunction<?>, Throwable> {

  private static final Pattern SPECIAL_CHARACTERS_REGEX = Pattern.compile("[\\(\\)&|]");
  private static final Pattern SPACES_REGEX = Pattern.compile("[ \\t]+");
  private static final Pattern METHOD_REGEX =
      Pattern.compile("^(GET|HEAD|POST|PUT|DELETE|CONNECT|OPTIONS|TRACE|PATCH) ");

  private final RouterFunction routerFunction;
  private final ServerRequest serverRequest;

  public RouteOnSuccessOrError(RouterFunction routerFunction, ServerRequest serverRequest) {
    this.routerFunction = routerFunction;
    this.serverRequest = serverRequest;
  }

  @Override
  public void accept(HandlerFunction<?> handler, Throwable throwable) {
    if (handler != null) {
      String predicateString = parsePredicateString();
      if (predicateString != null) {
        Context context = (Context) serverRequest.attributes().get(AdviceUtils.CONTEXT_ATTRIBUTE);
        if (context != null) {
          if (SpringWebfluxConfig.captureExperimentalSpanAttributes()) {
            Span span = Span.fromContext(context);
            span.setAttribute("spring-webflux.request.predicate", predicateString);
          }

          Span serverSpan = ServerSpan.fromContextOrNull(context);
          if (serverSpan != null) {
            serverSpan.updateName(ServletContextPath.prepend(context, parseRoute(predicateString)));
          }
        }
      }
    }
  }

  private String parsePredicateString() {
    String routerFunctionString = routerFunction.toString();
    // Router functions containing lambda predicates should not end up in span tags since they are
    // confusing
    if (routerFunctionString.startsWith(
        "org.springframework.web.reactive.function.server.RequestPredicates$$Lambda$")) {
      return null;
    } else {
      return routerFunctionString.replaceFirst("\\s*->.*$", "");
    }
  }

  private static String parseRoute(String routerString) {
    return METHOD_REGEX
        .matcher(
            SPACES_REGEX
                .matcher(SPECIAL_CHARACTERS_REGEX.matcher(routerString).replaceAll(""))
                .replaceAll(" ")
                .trim())
        .replaceAll("");
  }
}
