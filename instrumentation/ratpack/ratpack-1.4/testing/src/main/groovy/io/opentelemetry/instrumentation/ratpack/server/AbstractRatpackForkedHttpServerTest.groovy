/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import ratpack.error.ServerErrorHandler
import ratpack.exec.Execution
import ratpack.exec.Promise
import ratpack.exec.Result
import ratpack.exec.util.ParallelBatch
import ratpack.server.RatpackServer

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class AbstractRatpackForkedHttpServerTest extends AbstractRatpackHttpServerTest {

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
            }.fork().then { endpoint ->
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
          it.all { context ->
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
          it.all { context ->
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
          it.all { context ->
            Promise.sync {
              PATH_PARAM
            }.fork().then { endpoint ->
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
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status)
                context.response.headers.set("X-Test-Response", context.request.headers.get("X-Test-Request"))
                context.response.send(endpoint.body)
              }
            }
          }
        }
        it.prefix("fork_and_yieldAll") {
          it.all { context ->
            def promise = Promise.async { upstream ->
              Execution.fork().start({
                upstream.accept(Result.success(SUCCESS))
              })
            }
            ParallelBatch.of(promise).yieldAll().flatMap { list ->
              Promise.sync { list.get(0).value }
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
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

  def "test fork and yieldAll"() {
    setup:
    def url = address.resolve("fork_and_yieldAll").toString()
    url = url.replace("http://", "h1c://")
    def request = AggregatedHttpRequest.of(HttpMethod.GET, url)
    AggregatedHttpResponse response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == SUCCESS.status
    response.contentUtf8() == SUCCESS.body

    assertTraces(1) {
      trace(0, 2 + (hasHandlerSpan(SUCCESS) ? 1 : 0)) {
        span(0) {
          name "/fork_and_yieldAll"
          kind SpanKind.SERVER
          hasNoParent()
        }
        if (hasHandlerSpan(SUCCESS)) {
          span(1) {
            name "/fork_and_yieldAll"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
          span(2) {
            name "controller"
            kind SpanKind.INTERNAL
            childOf span(1)
          }
        } else {
          span(1) {
            name "controller"
            kind SpanKind.INTERNAL
            childOf span(0)
          }
        }
      }
    }
  }
}
