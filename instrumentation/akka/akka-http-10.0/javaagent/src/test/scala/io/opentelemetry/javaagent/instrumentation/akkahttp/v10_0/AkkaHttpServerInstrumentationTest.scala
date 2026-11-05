/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.function.Consumer

class AkkaHttpServerInstrumentationTest
    extends AbstractHttpServerInstrumentationTest {
  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    AkkaHttpTestWebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    AkkaHttpTestWebServer.stop()

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    configureRouteServer(options)
  }

  @Test def testPathMatchers(): Unit = {
    // /test1 / IntNumber / HexIntNumber / LongNumber / HexLongNumber / DoubleNumber / JavaUUID / Remaining
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      h1Address
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
      h1Address.resolve("/test2/second").toString
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
