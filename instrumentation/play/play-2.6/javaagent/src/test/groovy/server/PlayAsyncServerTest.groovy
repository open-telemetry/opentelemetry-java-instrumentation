/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import play.BuiltInComponents
import play.Mode
import play.libs.concurrent.HttpExecution
import play.mvc.Controller
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

class PlayAsyncServerTest extends PlayServerTest {
  @Shared
  def executor = Executors.newCachedThreadPool()

  def cleanupSpec() {
    executor.shutdown()
  }

  @Override
  Server startServer(int port) {
    def execContext = HttpExecution.fromThread(executor)
    return Server.forRouter(Mode.TEST, port) { BuiltInComponents components ->
      RoutingDsl.fromComponents(components)
        .GET(SUCCESS.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(SUCCESS) {
            Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
          }
        }, execContext)
      } as Supplier)
        .GET(QUERY_PARAM.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(QUERY_PARAM) {
            Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
          }
        }, execContext)
      } as Supplier)
        .GET(REDIRECT.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(REDIRECT) {
            Results.found(REDIRECT.getBody())
          }
        }, execContext)
      } as Supplier)
        .GET(ERROR.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(ERROR) {
            Results.status(ERROR.getStatus(), ERROR.getBody())
          }
        }, execContext)
      } as Supplier)
        .GET(EXCEPTION.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(EXCEPTION) {
            throw new Exception(EXCEPTION.getBody())
          }
        }, execContext)
      } as Supplier)
        .GET(CAPTURE_HEADERS.getPath()).routeAsync({
        def request = Controller.request()
        def response = Controller.response()
        CompletableFuture.supplyAsync({
          controller(CAPTURE_HEADERS) {
            request.header("X-Test-Request").ifPresent({ value ->
              response.setHeader("X-Test-Response", value)
            })
            Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
          }
        }, execContext)
      } as Supplier)
        .GET(INDEXED_CHILD.getPath()).routeAsync({
        String id = Controller.request().getQueryString("id")
        CompletableFuture.supplyAsync({
          controller(INDEXED_CHILD) {
            INDEXED_CHILD.collectSpanAttributes { name -> name == "id" ? id : null }
            Results.status(INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody())
          }
        }, execContext)
      } as Supplier)
        .build()
    }
  }
}
