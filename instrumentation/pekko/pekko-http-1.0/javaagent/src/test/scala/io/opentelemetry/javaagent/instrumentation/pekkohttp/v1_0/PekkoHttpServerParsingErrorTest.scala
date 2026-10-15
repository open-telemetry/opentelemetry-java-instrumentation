/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion
import io.opentelemetry.semconv.{
  ErrorAttributes,
  HttpAttributes,
  NetworkAttributes,
  UrlAttributes
}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.settings.ServerSettings
import org.apache.pekko.stream.scaladsl.Flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

import java.io.{BufferedReader, InputStreamReader, OutputStreamWriter}
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

import PekkoHttpServerParsingErrorTest.send

/** A request that pekko-http rejects while parsing it never becomes an
  * HttpRequest and never reaches the user handler, so it is traced through the
  * parsing error handler rather than through the regular server
  * instrumentation. On pekko-http 1.x nothing of the rejected request is
  * recovered, so the span carries only the response status. pekko-http 2.0
  * reports what was read of the request before it was rejected, and the span
  * carries that as well.
  */
class PekkoHttpServerParsingErrorTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create()

  @Test def illegalRequestTargetIsTraced(): Unit = {
    // this test does not share the web server used by the other tests, it needs a server that is
    // guaranteed to be running and no other test may terminate its actor system
    implicit val system: ActorSystem = ActorSystem("parsing-error-test")
    try {
      val handler: HttpRequest => HttpResponse = _ => HttpResponse()
      // bind to an ephemeral port rather than picking one up front, picking one leaves a window
      // where another process can claim it before pekko binds
      val binding =
        Await.result(
          PekkoHttpTestBinding.bindSync(handler, "localhost", 0),
          10.seconds
        )
      val port = binding.localAddress.getPort

      try {
        val response = send(
          port,
          "GET /%%?q=1 HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n"
        )
        assertThat(response.head).contains("400 Bad Request")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                // the request is rejected on its request target, before the protocol was read
                assertParsingErrorSpan(
                  span,
                  400,
                  method = Some("GET"),
                  path = Some("/%%"),
                  query = Some("q=1")
                )
                assertResponseHasCustomizedHeaders(span, response)
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** pekko-http answers a method it does not know with a 501 while parsing the
    * method token, so that request never reaches the handler either. The method
    * is what failed to parse, so the span does not report one, not even
    * `_OTHER`, and nothing after it was read, so even on pekko-http 2.0 the
    * span carries only the response status.
    */
  @Test def unknownMethodIsTraced(): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-unknown-method-test"
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
          "TEST /success HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n"
        )
        assertThat(response.head).contains("501 Not Implemented")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                assertParsingErrorSpan(span, 501)
                assertResponseHasCustomizedHeaders(span, response)
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** The parsing error handler also answers failures that happen after a
    * request was delivered, and those already have a span of their own, so only
    * a rejection made before delivery may produce one of these spans. A handled
    * request followed by a rejected one on the same connection shows the two
    * side by side. The rejected request reports its own method and target, not
    * those of the request before it on the connection.
    */
  @Test def rejectionAfterHandledRequestIsTraced(): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-keep-alive-test"
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
        val host = "Host: localhost:" + port + "\r\n"
        // the first request parses and is handled, the second fails on its request target
        send(
          port,
          "GET /already/handled HTTP/1.1\r\n" + host + "\r\n" +
            "POST /%% HTTP/1.1\r\n" + host + "\r\n"
        )

        testing.waitAndAssertTraces(
          new Consumer[TraceAssert] {
            override def accept(trace: TraceAssert): Unit =
              trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  span.hasName("GET").hasKind(SpanKind.SERVER)
                  ()
                }
              })
          },
          new Consumer[TraceAssert] {
            override def accept(trace: TraceAssert): Unit =
              trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit = {
                  assertParsingErrorSpan(
                    span,
                    400,
                    method = Some("POST"),
                    path = Some("/%%")
                  )
                  ()
                }
              })
          }
        )
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** Not every rejection comes from the parser. The stage that resolves a
    * request to an absolute uri rejects a CONNECT request itself, and that
    * request never reached the handler either, so it is traced the same way.
    * Its request-target is an authority rather than a path, so no `url.path` is
    * reported.
    */
  @Test def connectRequestIsTraced(): Unit = {
    implicit val system: ActorSystem = ActorSystem("parsing-error-connect-test")
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
          "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com\r\n\r\n"
        )
        assertThat(response.head).contains("400 Bad Request")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                assertParsingErrorSpan(
                  span,
                  400,
                  method = Some("CONNECT"),
                  protocolVersion = Some("1.1")
                )
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** A user written handler is instrumented too, and the span reports the
    * response that handler built. This handler delegates to the default one,
    * which is instrumented as well, and the rejection still produces a single
    * span.
    */
  @Test def customHandlerIsTraced(): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-custom-handler-test"
    )
    try {
      val handler: HttpRequest => HttpResponse = _ => HttpResponse()
      val settings = ServerSettings(system).withParsingErrorHandler(
        classOf[DelegatingParsingErrorHandler].getName
      )
      val binding =
        Await.result(
          // newServerAt is in both pekko-http 1.x and 2.x and is the only one of the two binding
          // apis that takes custom settings in both
          Http()
            .newServerAt("localhost", 0)
            .withSettings(settings)
            .bindSync(handler),
          10.seconds
        )
      val port = binding.localAddress.getPort

      try {
        val response = send(
          port,
          "GET /%% HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n"
        )
        assertThat(response.head).contains("418")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                assertParsingErrorSpan(
                  span,
                  418,
                  method = Some("GET"),
                  path = Some("/%%")
                )
                assertResponseHasCustomizedHeaders(span, response)
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** A request entity that fails to parse is answered by the parsing error
    * handler as well, but that request was delivered and already has a span, so
    * the failure must not add another one.
    */
  @Test def malformedRequestEntityIsNotTracedAgain(): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-malformed-entity-test"
    )
    try {
      // the handler reads the entity, so the entity failure fails the response stream
      val handler =
        Flow[HttpRequest].flatMapConcat(request =>
          request.entity.dataBytes.map(_ => HttpResponse())
        )
      val binding =
        Await.result(
          Http().newServerAt("localhost", 0).bindFlow(handler),
          10.seconds
        )
      val port = binding.localAddress.getPort

      try {
        val response = send(
          port,
          "POST / HTTP/1.1\r\nHost: localhost:" + port +
            "\r\nTransfer-Encoding: chunked\r\n\r\ninvalid-chunk-size\r\n"
        )
        assertThat(response.head).contains("400 Bad Request")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span.hasName("POST").hasKind(SpanKind.SERVER).hasNoParent()
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** The parts of the rejected request that pekko-http 2.0 reports, the
    * arguments name what it reports for the request in each test. pekko-http
    * 1.x reports none of them, so on 1.x every one of these spans looks the
    * same apart from the status the rejection was answered with.
    */
  private def assertParsingErrorSpan(
      span: SpanDataAssert,
      statusCode: Long,
      method: Option[String] = None,
      path: Option[String] = None,
      query: Option[String] = None,
      protocolVersion: Option[String] = None
  ): SpanDataAssert = {
    def ifRequestContext(
        key: AttributeKey[String],
        value: Option[String]
    ): Option[AttributeAssertion] =
      if (PekkoHttpServerParsingErrorTest.requestContextAvailable)
        value.map(equalTo(key, _))
      else None

    val attributes = Seq(
      Some(
        equalTo(
          HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
          java.lang.Long.valueOf(statusCode)
        )
      ),
      if (statusCode >= 500)
        Some(equalTo(ErrorAttributes.ERROR_TYPE, statusCode.toString))
      else None,
      ifRequestContext(HttpAttributes.HTTP_REQUEST_METHOD, method),
      ifRequestContext(UrlAttributes.URL_PATH, path),
      ifRequestContext(UrlAttributes.URL_QUERY, query),
      ifRequestContext(
        NetworkAttributes.NETWORK_PROTOCOL_VERSION,
        protocolVersion
      )
    ).flatten

    val name =
      if (PekkoHttpServerParsingErrorTest.requestContextAvailable)
        method.getOrElse("HTTP")
      else "HTTP"
    span
      .hasName(name)
      .hasKind(SpanKind.SERVER)
      .hasNoParent()
    if (statusCode >= 500) {
      span.hasStatus(StatusData.error())
    }
    span.hasAttributesSatisfyingExactly(attributes: _*)
  }

  /** The response customizer runs on the rejection too, so the response carries
    * the ids of the span that describes it, the same check the shared server
    * tests make.
    */
  private def assertResponseHasCustomizedHeaders(
      span: SpanDataAssert,
      response: List[String]
  ): SpanDataAssert = {
    val headers = response.tail
      .takeWhile(_.nonEmpty)
      .map { line =>
        val colon = line.indexOf(':')
        line.substring(0, colon).trim.toLowerCase -> line
          .substring(colon + 1)
          .trim
      }
      .toMap
    assertThat(headers.contains("x-test-traceid")).isTrue
    assertThat(headers.contains("x-test-spanid")).isTrue
    span
      .hasTraceId(headers("x-test-traceid"))
      .hasSpanId(headers("x-test-spanid"))
  }

}

object PekkoHttpServerParsingErrorTest {

  /** Whether the parsing error handler is told what was read of the rejected
    * request, which pekko-http 2.0 added.
    */
  val requestContextAvailable: Boolean =
    try {
      Class.forName("org.apache.pekko.http.IllegalRequestContext")
      true
    } catch {
      case _: ClassNotFoundException => false
    }

  /** Sends a raw request, which a client library would refuse to send, and
    * returns the lines of the response.
    */
  def send(port: Int, request: String): List[String] = {
    val socket = new Socket("localhost", port)
    try {
      val out =
        new OutputStreamWriter(
          socket.getOutputStream,
          StandardCharsets.ISO_8859_1
        )
      out.write(request)
      out.flush()

      val in = new BufferedReader(
        new InputStreamReader(socket.getInputStream, StandardCharsets.US_ASCII)
      )
      val lines = ListBuffer[String]()
      var line = in.readLine()
      while (line != null) {
        lines += line
        line = in.readLine()
      }
      lines.toList
    } finally socket.close()
  }
}
