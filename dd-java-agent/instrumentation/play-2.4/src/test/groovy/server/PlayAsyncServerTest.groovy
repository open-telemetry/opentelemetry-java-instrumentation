package server

import play.libs.concurrent.HttpExecution
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

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
        .GET(REDIRECT.getPath()).routeAsync({
        CompletableFuture.supplyAsync({
          controller(REDIRECT) {
            Results.found(REDIRECT.getBody())
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
