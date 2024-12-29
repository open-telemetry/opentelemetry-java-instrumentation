/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.extension.RegisterExtension;
import play.libs.concurrent.HttpExecution;
import play.mvc.Results;
import play.routing.RoutingDsl;
import play.server.Server;

class PlayAsyncServerTest extends PlayServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Server setupServer() {
    RoutingDsl router =
        new RoutingDsl()
            .GET(SUCCESS.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () ->
                            controller(
                                SUCCESS,
                                () -> Results.status(SUCCESS.getStatus(), SUCCESS.getBody())),
                        HttpExecution.defaultContext()))
            .GET(INDEXED_CHILD.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () ->
                            controller(
                                INDEXED_CHILD,
                                () -> {
                                  INDEXED_CHILD.collectSpanAttributes(
                                      it ->
                                          play.mvc.Http.Context.Implicit.request()
                                              .getQueryString(it));
                                  return Results.status(
                                      INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody());
                                }),
                        HttpExecution.defaultContext()))
            .GET(QUERY_PARAM.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () ->
                            controller(
                                QUERY_PARAM,
                                () ->
                                    Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())),
                        HttpExecution.defaultContext()))
            .GET(REDIRECT.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () -> controller(REDIRECT, () -> Results.found(REDIRECT.getBody())),
                        HttpExecution.defaultContext()))
            .GET(CAPTURE_HEADERS.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () ->
                            controller(
                                CAPTURE_HEADERS,
                                () ->
                                    Results.status(
                                            CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
                                        .withHeader(
                                            "X-Test-Response",
                                            play.mvc.Http.Context.Implicit.request()
                                                .getHeader("X-Test-Request"))),
                        HttpExecution.defaultContext()))
            .GET(ERROR.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () ->
                            controller(
                                ERROR, () -> Results.status(ERROR.getStatus(), ERROR.getBody())),
                        HttpExecution.defaultContext()))
            .GET(EXCEPTION.getPath())
            .routeAsync(
                () ->
                    CompletableFuture.supplyAsync(
                        () ->
                            controller(
                                EXCEPTION,
                                () -> {
                                  throw new IllegalArgumentException(EXCEPTION.getBody());
                                }),
                        HttpExecution.defaultContext()));

    return Server.forRouter(router.build(), port);
  }
}
