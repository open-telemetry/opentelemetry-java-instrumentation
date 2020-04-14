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

import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.test.embed.EmbeddedApp

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class RatpackAsyncHttpServerTest extends RatpackHttpServerTest {

  @Override
  EmbeddedApp startServer(int bindPort) {
    def ratpack = GroovyEmbeddedApp.ratpack {
      serverConfig {
        port bindPort
      }
      bindings {
        bind TestErrorHandler
      }
      handlers {
        prefix(SUCCESS.rawPath()) {
          all {
            Promise.sync {
              SUCCESS
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(QUERY_PARAM.rawPath()) {
          all {
            Promise.sync {
              QUERY_PARAM
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(request.query)
              }
            }
          }
        }
        prefix(REDIRECT.rawPath()) {
          all {
            Promise.sync {
              REDIRECT
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.redirect(endpoint.body)
              }
            }
          }
        }
        prefix(ERROR.rawPath()) {
          all {
            Promise.sync {
              ERROR
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(EXCEPTION.rawPath()) {
          all {
            Promise.sync {
              EXCEPTION
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                throw new Exception(endpoint.body)
              }
            }
          }
        }
        prefix("path/:id/param") {
          all {
            Promise.sync {
              PATH_PARAM
            }.fork().then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(pathTokens.id)
              }
            }
          }
        }
      }
    }
    ratpack.server.start()

    assert ratpack.address.port == bindPort
    return ratpack
  }
}
