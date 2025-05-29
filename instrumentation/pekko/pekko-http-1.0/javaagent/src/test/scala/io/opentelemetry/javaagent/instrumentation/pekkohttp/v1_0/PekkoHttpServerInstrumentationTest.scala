/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.util
import java.util.function.{BiFunction, Consumer, Function}

class PekkoHttpServerInstrumentationTest
    extends AbstractHttpServerInstrumentationTest {
  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    PekkoHttpTestWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    PekkoHttpTestWebServer.stop()

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    super.configure(options)
    // exception doesn't propagate
    options.setTestException(false)
    options.setTestPathParam(true)

    options.setHttpAttributes(
      new Function[ServerEndpoint, util.Set[AttributeKey[_]]] {
        override def apply(v1: ServerEndpoint): util.Set[AttributeKey[_]] = {
          HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES
        }
      }
    )

    val expectedRoute = new BiFunction[ServerEndpoint, String, String] {
      def apply(endpoint: ServerEndpoint, method: String): String = {
        if (endpoint eq ServerEndpoint.PATH_PARAM)
          return "/path/*/param"
        expectedHttpRoute(endpoint, method)
      }
    }
    options.setExpectedHttpRoute(expectedRoute)
  }

  @Test def testPathMatchers(): Unit = {
    // /test1 / IntNumber / HexIntNumber / LongNumber / HexLongNumber / DoubleNumber / JavaUUID / Remaining
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      address
        .resolve(
          "/test1/1/a1/2/b2/3.0/e58ed763-928c-4155-bee9-fdbaaadc15f3/remaining"
        )
        .toString
    )
    val response = client.execute(request).aggregate.join
    assertThat(response.status.code).isEqualTo(SUCCESS.getStatus)
    assertThat(response.contentUtf8).isEqualTo(SUCCESS.getBody)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
          override def accept(span: SpanDataAssert): Unit = {
            span.hasName("GET /test1/*/*/*/*/*/*/*")
          }
        })
    })
  }

  @Test def testConcat(): Unit = {
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      address.resolve("/test2/second").toString
    )
    val response = client.execute(request).aggregate.join
    assertThat(response.status.code).isEqualTo(SUCCESS.getStatus)
    assertThat(response.contentUtf8).isEqualTo(SUCCESS.getBody)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
          override def accept(span: SpanDataAssert): Unit = {
            span.hasName("GET /test2/second")
          }
        })
    })
  }
}
