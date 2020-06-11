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


import io.vertx.core.Future
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class VertxRxHttpServerTest extends VertxHttpServerTest {

  @Override
  protected Class<AbstractVerticle> verticle() {
    return VertxRxWebTestServer
  }

  static class VertxRxWebTestServer extends AbstractVerticle {

    @Override
    void start(final Future<Void> startFuture) {
      final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT)
      final Router router = Router.router(super.@vertx)

      router.route(SUCCESS.path).handler { ctx ->
        controller(SUCCESS) {
          ctx.response().setStatusCode(SUCCESS.status).end(SUCCESS.body)
        }
      }
      router.route(QUERY_PARAM.path).handler { ctx ->
        controller(QUERY_PARAM) {
          ctx.response().setStatusCode(QUERY_PARAM.status).end(ctx.request().query())
        }
      }
      router.route(REDIRECT.path).handler { ctx ->
        controller(REDIRECT) {
          ctx.response().setStatusCode(REDIRECT.status).putHeader("location", REDIRECT.body).end()
        }
      }
      router.route(ERROR.path).handler { ctx ->
        controller(ERROR) {
          ctx.response().setStatusCode(ERROR.status).end(ERROR.body)
        }
      }
      router.route(EXCEPTION.path).handler { ctx ->
        controller(EXCEPTION) {
          throw new Exception(EXCEPTION.body)
        }
      }

      super.@vertx.createHttpServer()
        .requestHandler { router.accept(it) }
        .listen(port) { startFuture.complete() }
    }
  }
}
