/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import play.libs.concurrent.HttpExecution
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import static play.mvc.Http.Context.Implicit.request

class PlayAsyncServerTest extends PlayServerTest {
  @Override
  Server startServer(int port) {
    def router =
      new RoutingDsl()
        .GET(SUCCESS.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(SUCCESS) {
            Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
          }
        }, HttpExecution.defaultContext())
      } as Supplier)
        .GET(INDEXED_CHILD.getPath()).routeTo({
        CompletableFuture.supplyAsync({
          controller(INDEXED_CHILD) {
            INDEXED_CHILD.collectSpanAttributes { request().getQueryString(it) }
            Results.status(INDEXED_CHILD.getStatus())
          }
        }, HttpExecution.defaultContext())
      } as Supplier)
        .GET(QUERY_PARAM.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(QUERY_PARAM) {
            Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
          }
        }, HttpExecution.defaultContext())
      } as Supplier)
        .GET(REDIRECT.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(REDIRECT) {
            Results.found(REDIRECT.getBody())
          }
        }, HttpExecution.defaultContext())
      } as Supplier)
        .GET(CAPTURE_HEADERS.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(CAPTURE_HEADERS) {
            Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
              .withHeader("X-Test-Response", request().getHeader("X-Test-Request"))
          }
        }, HttpExecution.defaultContext())
      } as Supplier)
        .GET(ERROR.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(ERROR) {
            Results.status(ERROR.getStatus(), ERROR.getBody())
          }
        }, HttpExecution.defaultContext())
      } as Supplier)
        .GET(EXCEPTION.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(EXCEPTION) {
            throw new Exception(EXCEPTION.getBody())
          }
        }, HttpExecution.defaultContext())
      } as Supplier)

    return Server.forRouter(router.build(), port)
  }
}
