/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Promise
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

class VertxRxCircuitBreakerHttpServerTest extends VertxRxHttpServerTest {

  @Override
  protected Class<AbstractVerticle> verticle() {
    return VertxRxCircuitBreakerWebTestServer
  }

  static class VertxRxCircuitBreakerWebTestServer extends AbstractVerticle {

    @Override
    void start(Promise<Void> startPromise) {
      int port = config().getInteger(CONFIG_HTTP_SERVER_PORT)
      Router router = Router.router(super.@vertx)
      CircuitBreaker breaker =
        CircuitBreaker.create(
          "my-circuit-breaker",
          super.@vertx,
          new CircuitBreakerOptions()
            .setTimeout(-1) // Disable the timeout otherwise it makes each test take this long.
        )

      router.route(SUCCESS.path).handler { ctx ->
        breaker.execute({ future ->
          future.complete(SUCCESS)
        }, { it ->
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(endpoint.body)
          }
        })
      }
      router.route(INDEXED_CHILD.path).handler { ctx ->
        breaker.execute({ future ->
          future.complete(INDEXED_CHILD)
        }, { it ->
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            endpoint.collectSpanAttributes { ctx.request().params().get(it) }
            ctx.response().setStatusCode(endpoint.status).end()
          }
        })
      }
      router.route(QUERY_PARAM.path).handler { ctx ->
        breaker.execute({ future ->
          future.complete(QUERY_PARAM)
        }, { it ->
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(ctx.request().query())
          }
        })
      }
      router.route(REDIRECT.path).handler { ctx ->
        breaker.execute({ future ->
          future.complete(REDIRECT)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).putHeader("location", endpoint.body).end()
          }
        })
      }
      router.route(ERROR.path).handler { ctx ->
        breaker.execute({ future ->
          future.complete(ERROR)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(endpoint.body)
          }
        })
      }
      router.route(EXCEPTION.path).handler { ctx ->
        breaker.execute({ future ->
          future.fail(new Exception(EXCEPTION.body))
        }, {
          try {
            def cause = it.cause()
            controller(EXCEPTION) {
              throw cause
            }
          } catch (Exception ex) {
            ctx.response().setStatusCode(EXCEPTION.status).end(ex.message)
          }
        })
      }
      router.route("/path/:id/param").handler { ctx ->
        breaker.execute({ future ->
          future.complete(PATH_PARAM)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(ctx.request().getParam("id"))
          }
        })
      }
      router.route(CAPTURE_HEADERS.path).handler { ctx ->
        breaker.execute({ future ->
          future.complete(CAPTURE_HEADERS)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status)
              .putHeader("X-Test-Response", ctx.request().getHeader("X-Test-Request"))
              .end(endpoint.body)
          }
        })
      }


      super.@vertx.createHttpServer()
        .requestHandler(router)
        .listen(port) { startPromise.complete() }
    }
  }

  @Override
  boolean hasExceptionOnServerSpan(ServerEndpoint endpoint) {
    return endpoint != EXCEPTION && super.hasExceptionOnServerSpan(endpoint)
  }
}
