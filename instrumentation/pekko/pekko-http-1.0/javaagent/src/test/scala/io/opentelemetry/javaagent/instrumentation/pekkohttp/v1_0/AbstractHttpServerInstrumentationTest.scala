/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.AbstractHttpServerInstrumentationTest.TIMEOUT
import io.opentelemetry.sdk.testing.assertj.{
  OpenTelemetryAssertions,
  TraceAssert
}
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util
import java.util.function.{Consumer, Function, Predicate}

object AbstractHttpServerInstrumentationTest {
  val TIMEOUT = new ServerEndpoint("TIMEOUT", "timeout", 503, "took too long")
}

abstract class AbstractHttpServerInstrumentationTest
    extends AbstractHttpServerTest[Object] {

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    options.setTestCaptureHttpHeaders(false)
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
    options.setHasResponseCustomizer(
      new Predicate[ServerEndpoint] {
        override def test(t: ServerEndpoint): Boolean =
          t != ServerEndpoint.EXCEPTION
      }
    )
    // pekko-http rejects an unknown method while parsing the request, so it is answered by the
    // sparse parsing error span, which reports _OTHER and the 501 and nothing else, while this
    // test also asserts server.address and http.request.method_original, and neither the Host
    // header nor the method token is recovered for a rejected request
    options.disableTestNonStandardHttpMethod
  }

  protected def protocolPrefix: String = "h1c://"

  @Test def testTimeout(): Unit = {
    val request = AggregatedHttpRequest.of(
      HttpMethod.GET,
      resolveAddress(TIMEOUT, protocolPrefix)
    )
    val response = client.execute(request).aggregate.join
    assertThat(response.status.code).isEqualTo(TIMEOUT.getStatus)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit = {
        trace.anySatisfy(new Consumer[SpanData] {
          override def accept(t: SpanData): Unit = assertServerSpan(
            OpenTelemetryAssertions.assertThat(t),
            "GET",
            TIMEOUT,
            TIMEOUT.getStatus
          )
        })
      }
    })
  }
}
