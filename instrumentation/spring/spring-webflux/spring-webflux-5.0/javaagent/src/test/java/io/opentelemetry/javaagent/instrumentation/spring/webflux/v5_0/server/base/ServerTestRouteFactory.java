/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.base;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.base.SpringWebFluxServerTest.NESTED_PATH;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.ServerResponse.BodyBuilder;
import reactor.core.publisher.Mono;

public abstract class ServerTestRouteFactory {
  public RouterFunction<ServerResponse> createRoutes() {
    return route(
            GET("/success"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.SUCCESS;

              return respond(endpoint, null, null, null);
            })
        .andRoute(
            GET("/query"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.QUERY_PARAM;

              return respond(endpoint, null, request.uri().getRawQuery(), null);
            })
        .andRoute(
            GET("/redirect"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.REDIRECT;

              return respond(
                  endpoint,
                  ServerResponse.status(endpoint.getStatus())
                      .header(HttpHeaders.LOCATION, endpoint.getBody()),
                  "",
                  null);
            })
        .andRoute(
            GET("/error-status"),
            redirect -> {
              ServerEndpoint endpoint = ServerEndpoint.ERROR;

              return respond(endpoint, null, null, null);
            })
        .andRoute(
            GET("/exception"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.EXCEPTION;

              return respond(
                  endpoint,
                  ServerResponse.ok(),
                  "",
                  () -> {
                    throw new IllegalStateException(endpoint.getBody());
                  });
            })
        .andRoute(
            GET("/path/{id}/param"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.PATH_PARAM;

              return respond(endpoint, null, request.pathVariable("id"), null);
            })
        .andRoute(
            GET("/child"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.INDEXED_CHILD;

              return respond(
                  endpoint,
                  null,
                  null,
                  () ->
                      Span.current()
                          .setAttribute(
                              "test.request.id", Long.parseLong(request.queryParam("id").get())));
            })
        .andRoute(
            GET("/captureHeaders"),
            request -> {
              ServerEndpoint endpoint = ServerEndpoint.CAPTURE_HEADERS;

              return respond(
                  endpoint,
                  ServerResponse.status(endpoint.getStatus())
                      .header(
                          "X-Test-Response",
                          request.headers().asHttpHeaders().getFirst("X-Test-Request")),
                  null,
                  null);
            })
        .andNest(
            path("/nestedPath"),
            nest(
                path("/hello"),
                route(
                    path("/world"),
                    request -> {
                      ServerEndpoint endpoint = NESTED_PATH;
                      return respond(endpoint, null, null, null);
                    })));
  }

  protected Mono<ServerResponse> respond(
      ServerEndpoint endpoint, BodyBuilder bodyBuilder, String body, Runnable spanAction) {
    if (bodyBuilder == null) {
      bodyBuilder = ServerResponse.status(endpoint.getStatus());
    }
    if (body == null) {
      body = endpoint.getBody() != null ? endpoint.getBody() : "";
    }
    if (spanAction == null) {
      spanAction = () -> {};
    }

    return wrapResponse(endpoint, bodyBuilder.syncBody(body), spanAction);
  }

  protected abstract Mono<ServerResponse> wrapResponse(
      ServerEndpoint endpoint, Mono<ServerResponse> response, Runnable spanAction);
}
