/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;

public class RouteOnSuccess implements Consumer<HandlerFunction<?>> {

  private static final Pattern SPECIAL_CHARACTERS_REGEX = Pattern.compile("[()&|]");
  private static final Pattern SPACES_REGEX = Pattern.compile("[ \\t]+");
  private static final Pattern METHOD_REGEX =
      Pattern.compile("^(GET|HEAD|POST|PUT|DELETE|CONNECT|OPTIONS|TRACE|PATCH) ");
  // router function string is "<predicate> -> <handler function>"
  private static final Pattern HANDLER_FUNCTION_REGEX = Pattern.compile("\\s*->.*");

  @Nullable private final String route;

  public RouteOnSuccess(RouterFunction<?> routerFunction) {
    this.route = parseRoute(parsePredicateString(routerFunction));
  }

  @Override
  public void accept(HandlerFunction<?> handler) {
    HttpServerRoute.update(Context.current(), HttpServerRouteSource.CONTROLLER, route);
  }

  @Nullable
  private static String parsePredicateString(RouterFunction<?> routerFunction) {
    // the handler function is frequently a lambda, so it has to be stripped before looking for a
    // lambda predicate
    String predicate = HANDLER_FUNCTION_REGEX.matcher(routerFunction.toString()).replaceFirst("");
    // Router functions containing lambda predicates should not end up in span tags since they are
    // confusing. Lambda class names look like "Foo$$Lambda$14/0x..." before jdk 21 and
    // "Foo$$Lambda/0x..." starting from jdk 21.
    if (predicate.contains("$$Lambda")) {
      return null;
    }
    return predicate;
  }

  @Nullable
  private static String parseRoute(@Nullable String routerString) {
    if (routerString == null) {
      return null;
    }
    return METHOD_REGEX
        .matcher(
            SPACES_REGEX
                .matcher(SPECIAL_CHARACTERS_REGEX.matcher(routerString).replaceAll(""))
                .replaceAll(" ")
                .trim())
        .replaceAll("");
  }
}
