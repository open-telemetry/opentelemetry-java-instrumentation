/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.{
  LongAssertConsumer,
  StringAssertConsumer,
  equalTo,
  satisfies
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS
import io.opentelemetry.semconv.HttpAttributes.{
  HTTP_REQUEST_METHOD,
  HTTP_RESPONSE_STATUS_CODE,
  HTTP_ROUTE
}
import io.opentelemetry.semconv.NetworkAttributes.{
  NETWORK_PEER_ADDRESS,
  NETWORK_PEER_PORT,
  NETWORK_PROTOCOL_VERSION
}
import io.opentelemetry.semconv.ServerAttributes.{SERVER_ADDRESS, SERVER_PORT}
import io.opentelemetry.semconv.UrlAttributes.{URL_PATH, URL_SCHEME}
import io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL
import io.opentelemetry.testing.internal.armeria.client.WebClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.{AbstractLongAssert, AbstractStringAssert}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{AfterAll, BeforeAll, TestInstance}
import zio.{Fiber, Runtime, Unsafe}

import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractZioHttpTest {
  @RegisterExtension private val testing = AgentInstrumentationExtension.create

  private var port = 0
  private var client: WebClient = null
  private var server: Fiber.Runtime[Throwable, Nothing] = null

  @BeforeAll
  def setup(): Unit = {
    port = PortUtils.findOpenPort
    val application = new ZioHttpTestApplication(port)
    server = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.fork(application.run)
    }
    assertThat(application.started.await(10, TimeUnit.SECONDS)).isTrue()
    client = WebClient.of("h1c://localhost:" + port)
  }

  @AfterAll
  def cleanup(): Unit = {
    if (server != null) {
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(server.interrupt).getOrThrowFiberFailure()
      }
    }
  }

  protected def assertRoute(route: String): Unit = {
    assertRoute(
      s"/$route/test",
      s"GET /$route/{name}",
      s"/$route/{name}",
      "Hello test"
    )
  }

  protected def assertDelegatedRoute(): Unit = {
    assertRoute(
      "/delegate/test",
      "GET /delegate/test",
      "/delegate/test",
      "Inner test"
    )
  }

  private def assertRoute(
      path: String,
      spanName: String,
      httpRoute: String,
      responseBody: String
  ): Unit = {
    val parent = testing.getOpenTelemetry
      .getTracer("test")
      .spanBuilder("parent")
      .startSpan()
    try {
      val context = parent.getSpanContext
      val response = client
        .prepare()
        .get(path)
        .header(
          "traceparent",
          s"00-${context.getTraceId}-${context.getSpanId}-01"
        )
        .execute()
        .aggregate
        .join
      assertThat(response.contentUtf8).isEqualTo(responseBody)
    } finally {
      parent.end()
    }

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span.hasName("parent").hasNoParent
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span
                .hasName(spanName)
                .hasKind(SpanKind.SERVER)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(URL_SCHEME, "http"),
                  equalTo(URL_PATH, path),
                  equalTo(HTTP_REQUEST_METHOD, "GET"),
                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                  satisfies(
                    USER_AGENT_ORIGINAL,
                    new StringAssertConsumer {
                      override def accept(
                          value: AbstractStringAssert[_]
                      ): Unit =
                        value.isInstanceOf(classOf[String])
                    }
                  ),
                  equalTo(HTTP_ROUTE, httpRoute),
                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                  equalTo(SERVER_ADDRESS, "localhost"),
                  equalTo(SERVER_PORT, port),
                  equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                  equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                  satisfies(
                    NETWORK_PEER_PORT,
                    new LongAssertConsumer {
                      override def accept(value: AbstractLongAssert[_]): Unit =
                        value.isInstanceOf(classOf[java.lang.Long])
                    }
                  )
                )
          }
        )
    })
  }
}
