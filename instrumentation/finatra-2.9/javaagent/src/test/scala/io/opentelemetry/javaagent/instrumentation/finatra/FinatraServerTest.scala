/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra

import com.twitter.finatra.http.HttpServer
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.api.internal.SemconvStability
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerInstrumentationExtension,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.{
  StringAssertConsumer,
  equalTo,
  satisfies
}
import io.opentelemetry.sdk.testing.assertj.{
  AttributeAssertion,
  SpanDataAssert,
  TraceAssert
}
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.semconv.CodeAttributes
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes
import org.assertj.core.api.AbstractStringAssert
import org.junit.jupiter.api.extension.RegisterExtension

import java.util
import java.util.concurrent.Executors
import java.util.function.{Consumer, Predicate}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class FinatraServerTest extends AbstractHttpServerTest[HttpServer] {

  @RegisterExtension val _: InstrumentationExtension =
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
    options.setTestPathParam(true)
    options.setHasHandlerSpan(new Predicate[ServerEndpoint] {
      override def test(endpoint: ServerEndpoint): Boolean =
        endpoint != ServerEndpoint.NOT_FOUND
    })
    options.setResponseCodeOnNonStandardHttpMethod(400)
  }

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  override protected def assertHandlerSpan(
      span: SpanDataAssert,
      method: String,
      endpoint: ServerEndpoint
  ): SpanDataAssert = {

    val assertions = new util.ArrayList[AttributeAssertion]
    if (SemconvStability.isEmitStableCodeSemconv) {
      assertions.add(
        satisfies(
          CodeAttributes.CODE_FUNCTION_NAME,
          new StringAssertConsumer {
            override def accept(t: AbstractStringAssert[_]): Unit = {
              t.startsWith(
                "io.opentelemetry.javaagent.instrumentation.finatra.FinatraController"
              )
              t.endsWith("apply")
            }
          }
        )
      )
    }
    if (SemconvStability.isEmitOldCodeSemconv) {
      assertions.add(
        satisfies(
          CodeIncubatingAttributes.CODE_NAMESPACE,
          new StringAssertConsumer {
            override def accept(t: AbstractStringAssert[_]): Unit = {
              t.startsWith(
                "io.opentelemetry.javaagent.instrumentation.finatra.FinatraController"
              )
            }
          }
        )
      )
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "apply"))
    }

    span
      .hasName(
        "FinatraController"
      )
      .hasKind(SpanKind.INTERNAL)
      .hasAttributesSatisfyingExactly(assertions)

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
