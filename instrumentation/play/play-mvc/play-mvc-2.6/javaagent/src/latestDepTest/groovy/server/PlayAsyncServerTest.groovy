/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import play.BuiltInComponents
import play.Mode
import play.libs.concurrent.HttpExecution
import play.mvc.Results
import play.routing.RequestFunctions
import play.routing.RoutingDsl
import play.server.Server
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

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
        .GET(SUCCESS.getPath()).routingAsync({
        CompletableFuture.supplyAsync({
          controller(SUCCESS) {
            Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .GET(QUERY_PARAM.getPath()).routingAsync({
        CompletableFuture.supplyAsync({
          controller(QUERY_PARAM) {
            Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .GET(REDIRECT.getPath()).routingAsync({
        CompletableFuture.supplyAsync({
          controller(REDIRECT) {
            Results.found(REDIRECT.getBody())
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .GET(ERROR.getPath()).routingAsync({
        CompletableFuture.supplyAsync({
          controller(ERROR) {
            Results.status(ERROR.getStatus(), ERROR.getBody())
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .GET(EXCEPTION.getPath()).routingAsync({
        CompletableFuture.supplyAsync({
          controller(EXCEPTION) {
            throw new Exception(EXCEPTION.getBody())
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .GET(CAPTURE_HEADERS.getPath()).routingAsync({request ->
        CompletableFuture.supplyAsync({
          controller(CAPTURE_HEADERS) {
            def result = Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
            request.header("X-Test-Request").ifPresent({ value ->
              result = result.withHeader("X-Test-Response", value)
            })
            result
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .GET(INDEXED_CHILD.getPath()).routingAsync({request ->
        String id = request.queryString("id").get()
        CompletableFuture.supplyAsync({
          controller(INDEXED_CHILD) {
            INDEXED_CHILD.collectSpanAttributes { name -> name == "id" ? id : null }
            Results.status(INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody())
          }
        }, execContext)
      } as RequestFunctions.Params0)
        .build()
    }
  }
}
