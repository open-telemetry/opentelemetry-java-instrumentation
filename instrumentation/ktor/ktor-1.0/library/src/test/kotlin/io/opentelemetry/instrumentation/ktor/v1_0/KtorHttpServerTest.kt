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
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class KtorHttpServerTest : AbstractHttpServerTest<ApplicationEngine>() {

  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = HttpServerInstrumentationExtension.forLibrary()
  }

  override fun setupServer(): ApplicationEngine {
    return embeddedServer(Netty, port = port) {
      KtorTestUtil.installOpenTelemetry(this, testing.openTelemetry)

      routing {
        get(ServerEndpoint.SUCCESS.path) {
          controller(ServerEndpoint.SUCCESS) {
            call.respondText(ServerEndpoint.SUCCESS.body, status = HttpStatusCode.fromValue(ServerEndpoint.SUCCESS.status))
          }
        }

        get(ServerEndpoint.REDIRECT.path) {
          controller(ServerEndpoint.REDIRECT) {
            call.respondRedirect(ServerEndpoint.REDIRECT.body)
          }
        }

        get(ServerEndpoint.ERROR.path) {
          controller(ServerEndpoint.ERROR) {
            call.respondText(ServerEndpoint.ERROR.body, status = HttpStatusCode.fromValue(ServerEndpoint.ERROR.status))
          }
        }

        get(ServerEndpoint.EXCEPTION.path) {
          controller(ServerEndpoint.EXCEPTION) {
            throw Exception(ServerEndpoint.EXCEPTION.body)
          }
        }

        get("/query") {
          controller(ServerEndpoint.QUERY_PARAM) {
            call.respondText("some=${call.request.queryParameters["some"]}", status = HttpStatusCode.fromValue(ServerEndpoint.QUERY_PARAM.status))
          }
        }

        get("/path/{id}/param") {
          controller(ServerEndpoint.PATH_PARAM) {
            call.respondText(
              call.parameters["id"]
                ?: "",
              status = HttpStatusCode.fromValue(ServerEndpoint.PATH_PARAM.status)
            )
          }
        }

        get("/child") {
          controller(ServerEndpoint.INDEXED_CHILD) {
            ServerEndpoint.INDEXED_CHILD.collectSpanAttributes { call.request.queryParameters[it] }
            call.respondText(ServerEndpoint.INDEXED_CHILD.body, status = HttpStatusCode.fromValue(ServerEndpoint.INDEXED_CHILD.status))
          }
        }

        get("/captureHeaders") {
          controller(ServerEndpoint.CAPTURE_HEADERS) {
            call.response.header("X-Test-Response", call.request.header("X-Test-Request") ?: "")
            call.respondText(ServerEndpoint.CAPTURE_HEADERS.body, status = HttpStatusCode.fromValue(ServerEndpoint.CAPTURE_HEADERS.status))
          }
        }
      }
    }.start()
  }

  override fun stopServer(server: ApplicationEngine) {
    server.stop(0, 10, TimeUnit.SECONDS)
  }

  // Copy in HttpServerTest.controller but make it a suspending function
  private suspend fun controller(endpoint: ServerEndpoint, wrapped: suspend () -> Unit) {
    assert(Span.current().spanContext.isValid, { "Controller should have a parent span. " })
    if (endpoint == ServerEndpoint.NOT_FOUND) {
      wrapped()
    }
    val span = testing.openTelemetry.getTracer("test").spanBuilder("controller").setSpanKind(SpanKind.INTERNAL).startSpan()
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

  override fun configure(options: HttpServerTestOptions) {
    options.setTestPathParam(true)

    options.setHttpAttributes {
      HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES - SemanticAttributes.NET_PEER_PORT
    }

    options.setExpectedHttpRoute {
      when (it) {
        ServerEndpoint.PATH_PARAM -> "/path/{id}/param"
        else -> expectedHttpRoute(it)
      }
    }
  }
}
