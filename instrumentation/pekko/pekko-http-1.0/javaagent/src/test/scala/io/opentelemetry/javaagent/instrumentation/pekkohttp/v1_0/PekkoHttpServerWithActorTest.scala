/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import java.net.URI

import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PekkoHttpServerWithActorTest {

  val setup = new PekkoHttpTestSetup()

  @BeforeAll def setupOptions(): Unit = {
    PekkoHttpTestWebServerWithActor.start(setup.getPort())
  }

  @AfterAll def cleanup(): Unit = {
    PekkoHttpTestWebServerWithActor.stop()
  }

  @Test def testSpan(): Unit = {
    val address = new URI(s"http://localhost:${setup.getPort()}/")
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      address.resolve("/test").toString
    )
    val response = setup.getClient().execute(request).aggregate.join
    assertThat(response.status.code).isEqualTo(200)
    val responseText = response.contentUtf8
    val splits = responseText.split("\n")
    assertThat(splits.length).isEqualTo(2)
    val routeSpan = splits(0).substring(6, splits(0).length)
    val actorSpan = splits(1).substring(6, splits(1).length)
    assertThat(routeSpan).isEqualTo(actorSpan)
  }
}
