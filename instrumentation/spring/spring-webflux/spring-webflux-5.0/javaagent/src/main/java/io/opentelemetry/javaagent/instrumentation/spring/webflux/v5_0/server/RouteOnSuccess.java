/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
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

  @Nullable private final String route;

  public RouteOnSuccess(RouterFunction<?> routerFunction) {
    this.route = parseRoute(parsePredicateString(routerFunction));
  }

  @Override
  public void accept(HandlerFunction<?> handler) {
    HttpRouteHolder.updateHttpRoute(
        Context.current(),
        HttpRouteSource.CONTROLLER,
        ServletContextPath.prepend(Context.current(), route));
  }

  private static String parsePredicateString(RouterFunction<?> routerFunction) {
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
