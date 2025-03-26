/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra

import com.twitter.finatra.http.HttpServer
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerInstrumentationExtension,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.concurrent.Executors
import java.util.function.Predicate
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class FinatraServerLatestTest extends AbstractHttpServerTest[HttpServer] {

  @RegisterExtension val extension: InstrumentationExtension =
    HttpServerInstrumentationExtension.forAgent()

  private val startupThread = Executors.newSingleThreadExecutor()

  override protected def setupServer(): HttpServer = {
    val testServer: FinatraServer = new FinatraServer()

    // Starting the server is blocking so start it in a separate thread
    implicit val ec: ExecutionContext =
      ExecutionContext.fromExecutor(startupThread)
    Future {
      testServer.nonExitingMain(Array("-admin.port=:0", "-http.port=:" + port))
    }
    testServer.awaitReady()
  }

  override protected def stopServer(server: HttpServer): Unit = {
    com.twitter.util.Await
      .ready(server.close(), com.twitter.util.Duration.fromSeconds(5))
    startupThread.shutdown()
  }

  override protected def configure(options: HttpServerTestOptions): Unit = {
    options.setHasHandlerSpan(new Predicate[ServerEndpoint] {
      override def test(endpoint: ServerEndpoint): Boolean =
        endpoint != ServerEndpoint.NOT_FOUND
    })
    options.setResponseCodeOnNonStandardHttpMethod(400)
  }

  override protected def assertHandlerSpan(
      span: SpanDataAssert,
      method: String,
      endpoint: ServerEndpoint
  ): SpanDataAssert = {
    span
      .hasName(
        "FinatraController"
      )
      .hasKind(SpanKind.INTERNAL)
      .hasAttributesSatisfyingExactly(
        equalTo(
          CodeIncubatingAttributes.CODE_NAMESPACE,
          "io.opentelemetry.javaagent.instrumentation.finatra.FinatraController"
        )
      )

    if (endpoint == ServerEndpoint.EXCEPTION) {
      span
        .hasStatus(StatusData.error())
        .hasException(
          new IllegalStateException(ServerEndpoint.EXCEPTION.getBody)
        )
    }

    span
  }
}
