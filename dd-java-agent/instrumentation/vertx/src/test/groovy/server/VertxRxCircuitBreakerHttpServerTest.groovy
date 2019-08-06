package server

import datadog.trace.agent.test.base.HttpServerTest
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class VertxRxCircuitBreakerHttpServerTest extends VertxHttpServerTest {

  @Override
  protected Class<AbstractVerticle> verticle() {
    return VertxRxCircuitBreakerWebTestServer
  }

  static class VertxRxCircuitBreakerWebTestServer extends AbstractVerticle {

    @Override
    void start(final io.vertx.core.Future<Void> startFuture) {
      final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT)
      final Router router = Router.router(super.@vertx)
      final CircuitBreaker breaker =
        CircuitBreaker.create(
          "my-circuit-breaker",
          super.@vertx,
          new CircuitBreakerOptions()
            .setTimeout(-1) // Disable the timeout otherwise it makes each test take this long.
        )

      router.route(SUCCESS.path).handler { ctx ->
        def result = breaker.execute { future ->
          future.complete(SUCCESS)
        }
        result.setHandler {
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(endpoint.body)
          }
        }
      }
      router.route(REDIRECT.path).handler { ctx ->
        def result = breaker.execute { future ->
          future.complete(REDIRECT)
        }
        result.setHandler {
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).putHeader("location", endpoint.body).end()
          }
        }
      }
      router.route(ERROR.path).handler { ctx ->
        def result = breaker.execute { future ->
          future.complete(ERROR)
        }
        result.setHandler {
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(endpoint.body)
          }
        }
      }
      router.route(EXCEPTION.path).handler { ctx ->
        def result = breaker.execute { future ->
          future.fail(new Exception(EXCEPTION.body))
        }
        result.setHandler {
          try {
            def cause = it.cause()
            controller(EXCEPTION) {
              throw cause
            }
          } catch (Exception ex) {
            ctx.response().setStatusCode(EXCEPTION.status).end(ex.message)
          }
        }
      }

      super.@vertx.createHttpServer()
        .requestHandler { router.accept(it) }
        .listen(port) { startFuture.complete() }
    }
  }
}
