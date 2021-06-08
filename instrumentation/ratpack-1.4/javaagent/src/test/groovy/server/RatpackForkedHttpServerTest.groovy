/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import ratpack.error.ServerErrorHandler
import ratpack.exec.Promise
import ratpack.server.RatpackServer

class RatpackForkedHttpServerTest extends RatpackHttpServerTest {

  @Override
  RatpackServer startServer(int bindPort) {

    def ratpack = RatpackServer.start {
      it.serverConfig {
        it.port(bindPort)
        it.address(InetAddress.getByName("localhost"))
      }
      it.handlers {
        it.register {
          it.add(ServerErrorHandler, new TestErrorHandler())
        }
        it.prefix(SUCCESS.rawPath()) {
          it.all {context ->
            Promise.sync {
              SUCCESS
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        it.prefix(INDEXED_CHILD.rawPath()) {
          it.all {context ->
            Promise.sync {
              INDEXED_CHILD
            }.fork().then {
              controller(INDEXED_CHILD) {
                INDEXED_CHILD.collectSpanAttributes { context.request.queryParams.get(it) }
                context.response.status(INDEXED_CHILD.status).send()
              }
            }
          }
        }
        it.prefix(QUERY_PARAM.rawPath()) {
          it.all { context ->
            Promise.sync {
              QUERY_PARAM
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(context.request.query)
              }
            }
          }
        }
        it.prefix(REDIRECT.rawPath()) {
          it.all {context ->
            Promise.sync {
              REDIRECT
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.redirect(endpoint.body)
              }
            }
          }
        }
        it.prefix(ERROR.rawPath()) {
          it.all {context ->
            Promise.sync {
              ERROR
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        it.prefix(EXCEPTION.rawPath()) {
          it.all {
            Promise.sync {
              EXCEPTION
            }.fork().then { endpoint ->
              controller(endpoint) {
                throw new Exception(endpoint.body)
              }
            }
          }
        }
        it.prefix("path/:id/param") {
          it.all {context ->
            Promise.sync {
              PATH_PARAM
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(context.pathTokens.id)
              }
            }
          }
        }
      }
    }

    assert ratpack.bindPort == bindPort
    assert ratpack.bindHost == 'localhost'
    return ratpack
  }
}
