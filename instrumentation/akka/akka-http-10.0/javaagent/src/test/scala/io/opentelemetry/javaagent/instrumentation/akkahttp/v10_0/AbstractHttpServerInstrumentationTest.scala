/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.semconv.HttpAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.{BufferedReader, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets.US_ASCII
import java.util
import java.util.function.{BiFunction, Consumer, Function, Predicate}

abstract class AbstractHttpServerInstrumentationTest
    extends AbstractHttpServerTest[Object] {

  @Test def malformedRequestTarget(): Unit = {
    val socket = new Socket("localhost", port)
    try {
      socket.setSoTimeout(10000)
      socket.getOutputStream.write(
        "GET /%% HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
          .getBytes(US_ASCII)
      )
      val reader =
        new BufferedReader(
          new InputStreamReader(socket.getInputStream, US_ASCII)
        )
      assertThat(reader.readLine()).isEqualTo("HTTP/1.1 400 Bad Request")
      while (reader.readLine() != null) {}
    } finally {
      socket.close()
    }

    testing().waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
          override def accept(span: SpanDataAssert): Unit = {
            span
              .hasName("HTTP")
              .hasKind(SpanKind.SERVER)
              .hasNoParent
              .hasStatus(StatusData.unset())
              .hasAttributesSatisfyingExactly(
                equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 400)
              )
          }
        })
    })
  }

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    configure(options, hasRoute = false)
  }

  protected def configure(
      options: HttpServerTestOptions,
      hasRoute: Boolean
  ): Unit = {
    options.setTestCaptureHttpHeaders(false)
    if (!hasRoute) {
      options.setHttpAttributes(
        new Function[ServerEndpoint, util.Set[AttributeKey[_]]] {
          override def apply(v1: ServerEndpoint): util.Set[AttributeKey[_]] = {
            val set = new util.HashSet[AttributeKey[_]](
              HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES
            )
            set.remove(HttpAttributes.HTTP_ROUTE)
            set
          }
        }
      )
    }
    options.setHasResponseCustomizer(
      new Predicate[ServerEndpoint] {
        override def test(t: ServerEndpoint): Boolean =
          t != ServerEndpoint.EXCEPTION
      }
    )
    // Akka rejects unknown methods before creating a request, so the method is unavailable.
    options.disableTestNonStandardHttpMethod
  }

  protected def configureRouteServer(options: HttpServerTestOptions): Unit = {
    configure(options, hasRoute = true)

    options.setTestException(false)
    options.setTestPathParam(true)
    options.setExpectedHttpRoute(
      new BiFunction[ServerEndpoint, String, String] {
        override def apply(
            endpoint: ServerEndpoint,
            method: String
        ): String =
          if (endpoint eq ServerEndpoint.PATH_PARAM) "/path/*/param"
          else expectedHttpRoute(endpoint, method)
      }
    )
  }
}
