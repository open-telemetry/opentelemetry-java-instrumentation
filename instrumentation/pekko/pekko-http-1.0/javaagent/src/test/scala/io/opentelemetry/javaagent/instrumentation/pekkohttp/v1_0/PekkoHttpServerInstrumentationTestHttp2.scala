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
  SpanDataAssert,
  TraceAssert
}
import io.opentelemetry.sdk.trace.data.{SpanData, StatusData}
import io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION
import io.opentelemetry.testing.internal.armeria.client.{
  ClientFactory,
  WebClient
}
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
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

  // an http/2 enabled binding serves http/1.1 connections as well, those requests are traced by
  // PekkoHttpServerTracer and the handler wrapper only makes the context available to user code
  @Test def testHttp1Request(): Unit = {
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      resolveAddress(ServerEndpoint.SUCCESS, "h1c://")
    )
    val response = client.execute(request).aggregate.join
    assertThat(response.status.code).isEqualTo(ServerEndpoint.SUCCESS.getStatus)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit = {
        trace.hasSpansSatisfyingExactly(
          serverSpan("1.1"),
          controllerSpan(trace)
        )
        ()
      }
    })
  }

  // the client negotiates an upgrade from http/1.1 to h2c instead of using the http/2 preface,
  // the request that is served over the upgraded connection must produce exactly one server span
  @Test def testHttp2Upgrade(): Unit = {
    val factory = ClientFactory.builder().useHttp2Preface(false).build()
    val response =
      try {
        WebClient
          .builder(s"h2c://localhost:$port")
          .factory(factory)
          .build()
          .get("/" + ServerEndpoint.SUCCESS.rawPath())
          .aggregate()
          .join()
      } finally {
        // close the connection before asserting so that any span that pekko-http ends when the
        // connection is closed is exported before the assertions run
        factory.close()
      }
    assertThat(response.status.code).isEqualTo(ServerEndpoint.SUCCESS.getStatus)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit = {
        trace.hasSpansSatisfyingExactly(
          serverSpan("2"),
          controllerSpan(trace)
        )
        ()
      }
    })
  }

  private def serverSpan(protocolVersion: String): Consumer[SpanDataAssert] =
    new Consumer[SpanDataAssert] {
      override def accept(span: SpanDataAssert): Unit = {
        span
          .hasName("GET")
          .hasKind(SpanKind.SERVER)
          .hasNoParent()
          .hasAttribute(NETWORK_PROTOCOL_VERSION, protocolVersion)
        ()
      }
    }

  private def controllerSpan(trace: TraceAssert): Consumer[SpanDataAssert] =
    new Consumer[SpanDataAssert] {
      override def accept(span: SpanDataAssert): Unit = {
        span
          .hasName("controller")
          .hasKind(SpanKind.INTERNAL)
          .hasParent(trace.getSpan(0))
        ()
      }
    }
}
