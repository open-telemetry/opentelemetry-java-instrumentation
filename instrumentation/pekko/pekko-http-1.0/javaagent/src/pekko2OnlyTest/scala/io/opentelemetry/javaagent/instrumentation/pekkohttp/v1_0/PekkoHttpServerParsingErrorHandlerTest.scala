/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.{HttpAttributes, UrlAttributes}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.http.{IllegalRequestContext, ParsingErrorHandler}
import org.apache.pekko.http.javadsl.{model => jm}
import org.apache.pekko.http.scaladsl.model.{
  ErrorInfo,
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes
}
import org.apache.pekko.http.scaladsl.settings.ServerSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.util.function.Consumer
import scala.concurrent.Await
import scala.concurrent.duration._

import PekkoHttpServerParsingErrorTest.send

/** Custom parsing error handlers on pekko-http 2.0, which answers a rejection
  * through the handler overload that carries the rejected request. Its default
  * delegates to the overload that pekko-http 1.x calls, and both are
  * instrumented, so a rejection must still get exactly one span whichever of
  * the two a handler overrides.
  */
class PekkoHttpServerParsingErrorHandlerTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create()

  /** A handler that overrides only the overload carrying the request never
    * reaches the other overload, so this is traced only because that overload
    * is instrumented as well.
    */
  @Test def handlerOverridingRequestContextOverloadIsTraced(): Unit =
    assertRejectionTraced(classOf[RequestContextParsingErrorHandler])

  /** A handler that delegates from the overload carrying the request to the
    * other one passes through both instrumented methods for one rejection.
    */
  @Test def handlerDelegatingBetweenOverloadsIsTracedOnce(): Unit =
    assertRejectionTraced(classOf[DelegatingParsingErrorHandler])

  private def assertRejectionTraced(handlerClass: Class[_]): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-handler-test",
      ConfigFactory
        .parseString(
          "pekko.http.server.parsing.error-handler = \"" + handlerClass.getName + "\""
        )
        .withFallback(ConfigFactory.load())
    )
    try {
      val handler: HttpRequest => HttpResponse = _ => HttpResponse()
      val binding =
        Await.result(
          PekkoHttpTestBinding.bindSync(handler, "localhost", 0),
          10.seconds
        )
      val port = binding.localAddress.getPort

      try {
        val response = send(
          port,
          "PUT /%%?q=1 HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n"
        )
        // the custom handler answered the rejection
        assertThat(response.head).contains("418")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span
                  .hasName("PUT")
                  .hasKind(SpanKind.SERVER)
                  .hasNoParent()
                  .hasAttributesSatisfyingExactly(
                    equalTo(
                      HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                      java.lang.Long.valueOf(418)
                    ),
                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "PUT"),
                    equalTo(UrlAttributes.URL_PATH, "/%%"),
                    equalTo(UrlAttributes.URL_QUERY, "q=1")
                  )
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }
}

/** Answers from the overload carrying the request alone, the other overload
  * fails the test if it is ever reached.
  */
class RequestContextParsingErrorHandler extends ParsingErrorHandler {

  override def handle(
      status: StatusCode,
      error: ErrorInfo,
      log: LoggingAdapter,
      settings: ServerSettings
  ): jm.HttpResponse =
    throw new AssertionError("the overload without the request was called")

  override def handle(
      status: StatusCode,
      error: ErrorInfo,
      log: LoggingAdapter,
      settings: ServerSettings,
      context: IllegalRequestContext
  ): jm.HttpResponse =
    HttpResponse(StatusCodes.ImATeapot)
}

/** Answers from the overload without the request, reached from the one that
  * carries it.
  */
class DelegatingParsingErrorHandler extends ParsingErrorHandler {

  override def handle(
      status: StatusCode,
      error: ErrorInfo,
      log: LoggingAdapter,
      settings: ServerSettings
  ): jm.HttpResponse =
    HttpResponse(StatusCodes.ImATeapot)

  override def handle(
      status: StatusCode,
      error: ErrorInfo,
      log: LoggingAdapter,
      settings: ServerSettings,
      context: IllegalRequestContext
  ): jm.HttpResponse =
    handle(status, error, log, settings)
}
