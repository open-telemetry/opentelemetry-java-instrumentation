/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.QueryParams
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.DecoratingHttpServiceFunction
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServerBuilder
import com.linecorp.armeria.server.ServiceRequestContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import java.util.function.Function

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AbstractArmeriaHttpServerTest extends HttpServerTest<Server> {

  abstract ServerBuilder configureServer(ServerBuilder serverBuilder)

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint) {
    switch (endpoint) {
      case NOT_FOUND:
        // TODO(anuraaga): Revisit this when applying instrumenters to more libraries, Armeria
        // currently reports '/*' which is a fallback route.
        return "/*"
      default:
        return super.expectedHttpRoute(endpoint)
    }
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
      SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
      SemanticAttributes.HTTP_SERVER_NAME
    ]
    super.httpAttributes(endpoint) + extra
  }

  @Override
  Server startServer(int port) {
    ServerBuilder sb = Server.builder()

    sb.http(port)

    sb.service(SUCCESS.path) { ctx, req ->
      controller(SUCCESS) {
        HttpResponse.of(HttpStatus.valueOf(SUCCESS.status), MediaType.PLAIN_TEXT_UTF_8, SUCCESS.body)
      }
    }

    sb.service(REDIRECT.path) { ctx, req ->
      controller(REDIRECT) {
        HttpResponse.of(ResponseHeaders.of(HttpStatus.valueOf(REDIRECT.status), HttpHeaderNames.LOCATION, REDIRECT.body))
      }
    }

    sb.service(ERROR.path) { ctx, req ->
      controller(ERROR) {
        HttpResponse.of(HttpStatus.valueOf(ERROR.status), MediaType.PLAIN_TEXT_UTF_8, ERROR.body)
      }
    }

    sb.service(EXCEPTION.path) { ctx, req ->
      controller(EXCEPTION) {
        throw new Exception(EXCEPTION.body)
      }
    }

    sb.service("/query") { ctx, req ->
      controller(QUERY_PARAM) {
        HttpResponse.of(HttpStatus.valueOf(QUERY_PARAM.status), MediaType.PLAIN_TEXT_UTF_8, "some=${QueryParams.fromQueryString(ctx.query()).get("some")}")
      }
    }

    sb.service("/path/:id/param") { ctx, req ->
      controller(PATH_PARAM) {
        HttpResponse.of(HttpStatus.valueOf(PATH_PARAM.status), MediaType.PLAIN_TEXT_UTF_8, ctx.pathParam("id"))
      }
    }

    sb.service("/child") { ctx, req ->
      controller(INDEXED_CHILD) {
        INDEXED_CHILD.collectSpanAttributes { QueryParams.fromQueryString(ctx.query()).get(it) }
        HttpResponse.of(HttpStatus.valueOf(INDEXED_CHILD.status), MediaType.PLAIN_TEXT_UTF_8, INDEXED_CHILD.body)
      }
    }

    sb.service("/captureHeaders") { ctx, req ->
      controller(CAPTURE_HEADERS) {
        HttpResponse.of(
          ResponseHeaders.of(HttpStatus.valueOf(CAPTURE_HEADERS.status),
            "X-Test-Response", req.headers().get("X-Test-Request"),
            HttpHeaderNames.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8),
          HttpData.ofUtf8(CAPTURE_HEADERS.body))
      }
    }

    // Make sure user decorators see spans.
    sb.decorator(new DecoratingHttpServiceFunction() {
      @Override
      HttpResponse serve(HttpService delegate, ServiceRequestContext ctx, HttpRequest req) throws Exception {
        if (!Span.current().spanContext.isValid()) {
          // Return an invalid code to fail any assertion
          return HttpResponse.of(600)
        }
        ctx.addAdditionalResponseHeader("decoratinghttpservicefunction", "ok")
        return delegate.serve(ctx, req)
      }
    })

    sb.decorator(new Function<HttpService, HttpService>() {
      @Override
      HttpService apply(HttpService delegate) {
        return new HttpService() {
          @Override
          HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
            if (!Span.current().spanContext.isValid()) {
              // Return an invalid code to fail any assertion
              return HttpResponse.of(601)
            }
            ctx.addAdditionalResponseHeader("decoratingfunction", "ok")
            return delegate.serve(ctx, req)
          }
        }
      }
    })

    configureServer(sb)

    def server = sb.build()
    server.start().join()
    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  boolean testPathParam() {
    true
  }
}
