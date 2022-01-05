/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import ratpack.error.ServerErrorHandler
import ratpack.exec.Promise
import ratpack.server.RatpackServer

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AbstractRatpackAsyncHttpServerTest extends AbstractRatpackHttpServerTest {

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
          it.all { context ->
            Promise.sync {
              SUCCESS
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        it.prefix(INDEXED_CHILD.rawPath()) {
          it.all { context ->
            Promise.sync {
              INDEXED_CHILD
            } then {
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
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(context.request.query)
              }
            }
          }
        }
        it.prefix(REDIRECT.rawPath()) {
          it.all { context ->
            Promise.sync {
              REDIRECT
            } then { endpoint ->
              controller(endpoint) {
                context.redirect(endpoint.body)
              }
            }
          }
        }
        it.prefix(ERROR.rawPath()) {
          it.all { context ->
            Promise.sync {
              ERROR
            } then { endpoint ->
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
            } then { endpoint ->
              controller(endpoint) {
                throw new Exception(endpoint.body)
              }
            }
          }
        }
        it.prefix("path/:id/param") {
          it.all { context ->
            Promise.sync {
              PATH_PARAM
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(context.pathTokens.id)
              }
            }
          }
        }
        it.prefix(CAPTURE_HEADERS.rawPath()) {
          it.all { context ->
            Promise.sync {
              CAPTURE_HEADERS
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status)
                context.response.headers.set("X-Test-Response", context.request.headers.get("X-Test-Request"))
                context.response.send(endpoint.body)
              }
            }
          }
        }
      }
      configure(it)
    }

    assert ratpack.bindPort == bindPort
    assert ratpack.bindHost == 'localhost'
    return ratpack
  }
}
