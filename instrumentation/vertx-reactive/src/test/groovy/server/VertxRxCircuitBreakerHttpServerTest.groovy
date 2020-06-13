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
package server
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
import io.opentelemetry.auto.test.base.HttpServerTest
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Future
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class VertxRxCircuitBreakerHttpServerTest extends VertxRxHttpServerTest {

  @Override
  protected Class<AbstractVerticle> verticle() {
    return VertxRxCircuitBreakerWebTestServer
  }

  static class VertxRxCircuitBreakerWebTestServer extends AbstractVerticle {

    @Override
    void start(final Future<Void> startFuture) {
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
        breaker.executeCommand({ future ->
          future.complete(SUCCESS)
        }, { it ->
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(endpoint.body)
          }
        })
      }
      router.route(QUERY_PARAM.path).handler { ctx ->
        breaker.executeCommand({ future ->
          future.complete(QUERY_PARAM)
        }, { it ->
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(ctx.request().query())
          }
        })
      }
      router.route(REDIRECT.path).handler { ctx ->
        breaker.executeCommand({ future ->
          future.complete(REDIRECT)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).putHeader("location", endpoint.body).end()
          }
        })
      }
      router.route(ERROR.path).handler { ctx ->
        breaker.executeCommand({ future ->
          future.complete(ERROR)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(endpoint.body)
          }
        })
      }
      router.route(EXCEPTION.path).handler { ctx ->
        breaker.executeCommand({ future ->
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
        breaker.executeCommand({ future ->
          future.complete(PATH_PARAM)
        }, {
          if (it.failed()) {
            throw it.cause()
          }
          HttpServerTest.ServerEndpoint endpoint = it.result()
          controller(endpoint) {
            ctx.response().setStatusCode(endpoint.status).end(ctx.request().getParam("id"))
          }
        })
      }


      super.@vertx.createHttpServer()
        .requestHandler { router.accept(it) }
        .listen(port) { startFuture.complete() }
    }
  }
}
