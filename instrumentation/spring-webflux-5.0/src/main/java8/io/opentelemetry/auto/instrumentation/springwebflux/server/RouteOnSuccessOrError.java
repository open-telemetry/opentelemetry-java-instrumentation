/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.springwebflux.server;

import io.opentelemetry.trace.Span;
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
          span.setAttribute("request.predicate", predicateString);
        }
        final Span parentSpan =
            (Span) serverRequest.attributes().get(AdviceUtils.PARENT_SPAN_ATTRIBUTE);
        if (parentSpan != null) {
          parentSpan.updateName(parseRoute(predicateString));
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

  private String parseRoute(final String routerString) {
    return METHOD_REGEX
        .matcher(
            SPACES_REGEX
                .matcher(SPECIAL_CHARACTERS_REGEX.matcher(routerString).replaceAll(""))
                .replaceAll(" ")
                .trim())
        .replaceAll("");
  }
}
