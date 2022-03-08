/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.*
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

class TestServer {

  companion object {

    private val tracer = GlobalOpenTelemetry.getTracer("test")

    @JvmStatic
    fun startServer(port: Int, openTelemetry: OpenTelemetry): ApplicationEngine {
      return embeddedServer(Netty, port = port) {
        KtorTestUtil.installOpenTelemetry(this, openTelemetry)

        routing {
          get(SUCCESS.path) {
            controller(SUCCESS) {
              call.respondText(SUCCESS.body, status = HttpStatusCode.fromValue(SUCCESS.status))
            }
          }

          get(REDIRECT.path) {
            controller(REDIRECT) {
              call.respondRedirect(REDIRECT.body)
            }
          }

          get(ERROR.path) {
            controller(ERROR) {
              call.respondText(ERROR.body, status = HttpStatusCode.fromValue(ERROR.status))
            }
          }

          get(EXCEPTION.path) {
            controller(EXCEPTION) {
              throw Exception(EXCEPTION.body)
            }
          }

          get("/query") {
            controller(QUERY_PARAM) {
              call.respondText("some=${call.request.queryParameters["some"]}", status = HttpStatusCode.fromValue(QUERY_PARAM.status))
            }
          }

          get("/path/{id}/param") {
            controller(PATH_PARAM) {
              call.respondText(call.parameters["id"] ?: "", status = HttpStatusCode.fromValue(PATH_PARAM.status))
            }
          }

          get("/child") {
            controller(INDEXED_CHILD) {
              INDEXED_CHILD.collectSpanAttributes { call.request.queryParameters[it] }
              call.respondText(INDEXED_CHILD.body, status = HttpStatusCode.fromValue(INDEXED_CHILD.status))
            }
          }

          get("/captureHeaders") {
            controller(CAPTURE_HEADERS) {
              call.response.header("X-Test-Response", call.request.header("X-Test-Request") ?: "")
              call.respondText(CAPTURE_HEADERS.body, status = HttpStatusCode.fromValue(CAPTURE_HEADERS.status))
            }
          }
        }
      }.start()
    }

    // Copy in HttpServerTest.controller but make it a suspending function
    private suspend fun controller(endpoint: ServerEndpoint, wrapped: suspend () -> Unit) {
      assert(Span.current().spanContext.isValid, { "Controller should have a parent span. " })
      if (endpoint == NOT_FOUND) {
        wrapped()
      }
      val span = tracer.spanBuilder("controller").setSpanKind(SpanKind.INTERNAL).startSpan()
      try {
        withContext(Context.current().with(span).asContextElement()) {
          wrapped()
        }
        span.end()
      } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR)
        span.recordException(if (e is ExecutionException) e.cause ?: e else e)
        span.end()
        throw e
      }
    }
  }
}
