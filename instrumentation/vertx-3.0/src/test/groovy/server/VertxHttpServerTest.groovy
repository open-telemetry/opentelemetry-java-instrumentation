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

import io.opentelemetry.auto.test.base.HttpServerTest
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM

class VertxHttpServerTest extends HttpServerTest<Vertx> {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port"

  @Override
  Vertx startServer(int port) {
    Vertx server = Vertx.vertx(new VertxOptions()
    // Useful for debugging:
    // .setBlockedThreadCheckInterval(Integer.MAX_VALUE)
      .setClusterPort(port))
    final CompletableFuture<Void> future = new CompletableFuture<>()
    server.deployVerticle(verticle().getName(),
      new DeploymentOptions()
        .setConfig(new JsonObject().put(CONFIG_HTTP_SERVER_PORT, port))
        .setInstances(3)) { res ->
      if (!res.succeeded()) {
        throw new RuntimeException("Cannot deploy server Verticle", res.cause())
      }
      future.complete(null)
    }

    future.get(30, TimeUnit.SECONDS)
    return server
  }

  protected Class<? extends AbstractVerticle> verticle() {
    return VertxWebServer
  }

  @Override
  void stopServer(Vertx server) {
    server.close()
  }

  @Override
  boolean testExceptionBody() {
    return false
  }

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean testNotFound() {
    return false
  }

  @Override
  String expectedOperationName(String method, ServerEndpoint endpoint) {
    return endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.getPath()
  }

}
