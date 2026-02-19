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
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import zio.ExitCode

import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZioHttpTest {
  @RegisterExtension private val testing = AgentInstrumentationExtension.create

  var port = 0
  var client: WebClient = null

  @BeforeAll
  def setup(): Unit = {
    port = PortUtils.findOpenPort
    ZioHttpTestApplication.port = port
    val t = new Thread(() => {
      ZioHttpTestApplication.main(Array())
    })
    t.start()
    ZioHttpTestApplication.started.await(10, TimeUnit.SECONDS)
    client = WebClient.of("h1c://localhost:" + port)
  }

  @AfterAll
  def cleanup(): Unit = {
    ZioHttpTestApplication.exit(ExitCode.success)
  }

  @Test
  def spanName(): Unit = {
    val response = client.get("/greet/test").aggregate.join
    val content = response.contentUtf8
    assertThat(port).isNotEqualTo(0)
    assertThat(content).isEqualTo("Hello test")

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
          override def accept(span: SpanDataAssert): Unit = {
            span
              .hasName("GET /greet/{name}")
              .hasKind(SpanKind.SERVER)
              .hasNoParent
              .hasAttributesSatisfyingExactly(
                equalTo(URL_SCHEME, "http"),
                equalTo(URL_PATH, "/greet/test"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                satisfies(
                  USER_AGENT_ORIGINAL,
                  new StringAssertConsumer {
                    override def accept(t: AbstractStringAssert[_]): Unit =
                      t.isInstanceOf(classOf[String])
                  }
                ),
                equalTo(HTTP_ROUTE, "/greet/{name}"),
                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, port),
                equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                satisfies(
                  NETWORK_PEER_PORT,
                  new LongAssertConsumer {
                    override def accept(t: AbstractLongAssert[_]): Unit =
                      t.isInstanceOf(classOf[java.lang.Long])
                  }
                )
              )
          }
        })
    })

  }

}
