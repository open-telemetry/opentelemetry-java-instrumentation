/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import play.Mode;
import play.libs.concurrent.HttpExecution;
import play.mvc.Result;
import play.mvc.Results;
import play.routing.RoutingDsl;
import play.server.Server;
import scala.concurrent.ExecutionContextExecutor;

class PlayAsyncServerTest extends PlayServerTest {

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  @Override
  protected Server setupServer() {
    ExecutionContextExecutor executionContextExecutor = HttpExecution.fromThread(executor);
    return Server.forRouter(
        Mode.TEST,
        port,
        components ->
            RoutingDsl.fromComponents(components)
                .GET(SUCCESS.getPath())
                .routingAsync(
                    request ->
                        CompletableFuture.supplyAsync(
                            () ->
                                controller(
                                    SUCCESS,
                                    () -> Results.status(SUCCESS.getStatus(), SUCCESS.getBody())),
                            executionContextExecutor))
                .GET(QUERY_PARAM.getPath())
                .routingAsync(
                    request ->
                        CompletableFuture.supplyAsync(
                            () ->
                                controller(
                                    QUERY_PARAM,
                                    () ->
                                        Results.status(
                                            QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())),
                            executionContextExecutor))
                .GET(REDIRECT.getPath())
                .routingAsync(
                    request ->
                        CompletableFuture.supplyAsync(
                            () -> controller(REDIRECT, () -> Results.found(REDIRECT.getBody())),
                            executionContextExecutor))
                .GET(ERROR.getPath())
                .routingAsync(
                    request ->
                        CompletableFuture.supplyAsync(
                            () ->
                                controller(
                                    ERROR,
                                    () -> Results.status(ERROR.getStatus(), ERROR.getBody())),
                            executionContextExecutor))
                .GET(EXCEPTION.getPath())
                .routingAsync(
                    request ->
                        CompletableFuture.supplyAsync(
                            () ->
                                controller(
                                    EXCEPTION,
                                    () -> {
                                      throw new IllegalArgumentException(EXCEPTION.getBody());
                                    }),
                            executionContextExecutor))
                .GET(CAPTURE_HEADERS.getPath())
                .routingAsync(
                    request ->
                        CompletableFuture.supplyAsync(
                            () ->
                                controller(
                                    CAPTURE_HEADERS,
                                    () -> {
                                      Result result =
                                          Results.status(
                                              CAPTURE_HEADERS.getStatus(),
                                              CAPTURE_HEADERS.getBody());
                                      return request
                                          .header("X-Test-Request")
                                          .map(value -> result.withHeader("X-Test-Response", value))
                                          .orElse(result);
                                    }),
                            executionContextExecutor))
                .GET(INDEXED_CHILD.getPath())
                .routingAsync(
                    request -> {
                      String id = request.queryString("id").orElse(null);
                      return CompletableFuture.supplyAsync(
                          () ->
                              controller(
                                  INDEXED_CHILD,
                                  () -> {
                                    INDEXED_CHILD.collectSpanAttributes(
                                        name -> "id".equals(name) ? id : null);
                                    return Results.status(
                                        INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody());
                                  }),
                          executionContextExecutor);
                    })
                .build());
  }

  @Override
  protected void stopServer(Server server) {
    server.stop();
    executor.shutdown();
  }
}
