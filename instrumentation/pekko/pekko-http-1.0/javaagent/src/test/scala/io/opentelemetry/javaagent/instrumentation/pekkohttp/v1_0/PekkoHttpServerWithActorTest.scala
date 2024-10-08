/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import java.net.URI

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.testing.internal.armeria.client.{ClientFactory, WebClient}
import io.opentelemetry.testing.internal.armeria.client.logging.LoggingClient
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpHeaderNames,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{AfterAll, Test, TestInstance}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoHttpServerWithActorTest {

  private val port = PortUtils.findOpenPort()
  val server = new PekkoHttpTestWebServerWithActor(port)

  @AfterAll def cleanup(): Unit = {
    server.stop()
  }

  private val minute = java.time.Duration.ofMinutes(1)
  private val client = WebClient.builder()
    .responseTimeout(minute)
    .writeTimeout(minute)
    .factory(ClientFactory.builder().connectTimeout(minute).build())
    .setHeader(HttpHeaderNames.USER_AGENT, AbstractHttpServerUsingTest.TEST_USER_AGENT)
    .setHeader(HttpHeaderNames.X_FORWARDED_FOR, AbstractHttpServerUsingTest.TEST_CLIENT_IP)
    .decorator(LoggingClient.newDecorator())
    .build()

  @Test def testSpan(): Unit = {
    val address = new URI(s"http://localhost:$port")
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      address.resolve("/test").toString
    )
    val response = client.execute(request).aggregate.join
    assertThat(response.status.code).isEqualTo(200)
    val responseText = response.contentUtf8
    val splits = responseText.split("\n")
    assertThat(splits.length).isEqualTo(2)
    val routeSpan = splits(0).substring(6, splits(0).length)
    val actorSpan = splits(1).substring(6, splits(1).length)
    assertThat(routeSpan).isEqualTo(actorSpan)
  }
}
