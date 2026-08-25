/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  HttpServerInstrumentationExtension,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.sdk.testing.assertj.{
  OpenTelemetryAssertions,
  TraceAssert
}
import io.opentelemetry.sdk.trace.data.{SpanData, StatusData}
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.function.Consumer
import scala.util.Try

class PekkoHttpServerInstrumentationTestHttp2
    extends AbstractHttpServerInstrumentationTest {

  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  override protected def setupServer(): AnyRef = {
    PekkoHttpTestHttp2WebServer.start(port)
    null
  }

  override protected def stopServer(server: Object): Unit =
    PekkoHttpTestHttp2WebServer.stop()

  override protected def protocolPrefix: String = "h2c://"

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    super.configure(options)
    options.setTestHttpPipelining(false)
    options.useHttp2()
    // pekko-http tears down the http/2 connection when the handler future fails instead of
    // responding with 500, see testException for the span that is produced
    options.setTestException(false)
  }

  @Test def testException(): Unit = {
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      resolveAddress(ServerEndpoint.EXCEPTION, protocolPrefix)
    )
    // the connection is torn down without a response, we only care about the emitted span
    Try(client.execute(request).aggregate.join)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit = {
        trace.anySatisfy(new Consumer[SpanData] {
          override def accept(span: SpanData): Unit =
            OpenTelemetryAssertions
              .assertThat(span)
              .hasKind(SpanKind.SERVER)
              .hasStatus(StatusData.error())
              .hasException(
                new IllegalStateException(ServerEndpoint.EXCEPTION.getBody)
              )
        })
      }
    })
  }
}
