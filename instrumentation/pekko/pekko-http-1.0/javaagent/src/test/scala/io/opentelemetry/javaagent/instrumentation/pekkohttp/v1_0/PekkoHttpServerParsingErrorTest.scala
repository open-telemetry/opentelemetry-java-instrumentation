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
import org.junit.jupiter.api.{Test, TestInstance}
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
  @Test def connectRequestIsTraced(): Unit =
    assertConnectTraced("example.com:443")

  /** pekko-http renders an IPv6 authority with a leading `//`, which must not
    * be taken for an origin-form path.
    */
  @Test def ipv6ConnectRequestIsTraced(): Unit =
    assertConnectTraced("[::1]:443")

  private def assertConnectTraced(target: String): Unit = {
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
          "CONNECT " + target + " HTTP/1.1\r\nHost: example.com\r\n\r\n"
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

  /** An absolute-form request-target may have a query but no path, so a `/` in
    * that query must not be taken for the start of a path, which would report
    * part of the query as `url.path` and keep it from query redaction.
    */
  @Test def absoluteTargetWithoutPathIsTraced(): Unit =
    assertRejectedTargetTraced(
      "HTTP://example.com?q=/%%",
      scheme = Some("http"),
      query = Some("q=/%%")
    )

  @Test def httpsSchemeIsNormalized(): Unit =
    assertRejectedTargetTraced(
      "HtTpS://example.com/path?q=/%%",
      scheme = Some("https"),
      path = Some("/path"),
      query = Some("q=/%%")
    )

  @Test def nonHttpSchemeIsDropped(): Unit =
    assertRejectedTargetTraced(
      "custom://example.com/path?q=/%%",
      path = Some("/path"),
      query = Some("q=/%%")
    )

  @Test def absoluteTargetWithoutAuthorityIsTraced(): Unit =
    assertRejectedTargetTraced(
      "scheme:/path?q=1",
      path = Some("/path"),
      query = Some("q=1"),
      includeHost = false
    )

  /** A request-target has no fragment, and query redaction does not look past a
    * `#`, so whatever follows one in a rejected target is not reported.
    */
  @Test def fragmentOfRejectedTargetIsDropped(): Unit =
    assertRejectedTargetTraced(
      "/%%?q=1#&q=2",
      path = Some("/%%"),
      query = Some("q=1")
    )

  /** A rejected target keeps literal control characters, which the logging
    * exporter would write to the terminal unescaped, so neither the path nor
    * the query of such a target is reported.
    */
  @Test def targetWithControlCharactersIsNotReported(): Unit =
    assertRejectedTargetTraced("/%%\u001b[2J?q=1")

  /** A `://` further into a target that does not start with a scheme does not
    * make it an absolute-form target, so nothing before it is reported as the
    * scheme.
    */
  @Test def targetWithoutValidSchemeIsNotAbsolute(): Unit =
    assertRejectedTargetTraced("bad?q=1://example.com/%%")

  /** Sends a GET request with the given request-target, which pekko-http
    * rejects, and checks the parts of the target that its span reports.
    */
  private def assertRejectedTargetTraced(
      target: String,
      scheme: Option[String] = None,
      path: Option[String] = None,
      query: Option[String] = None,
      includeHost: Boolean = true
  ): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-rejected-target-test"
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
        val hostHeader = if (includeHost) "Host: example.com\r\n" else ""
        val response = send(
          port,
          "GET " + target + " HTTP/1.1\r\n" + hostHeader + "\r\n"
        )
        assertThat(response.head).contains("400 Bad Request")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                assertParsingErrorSpan(
                  span,
                  400,
                  method = Some("GET"),
                  scheme = scheme,
                  path = path,
                  query = query,
                  protocolVersion = if (includeHost) None else Some("1.1")
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
      scheme: Option[String] = None,
      path: Option[String] = None,
      query: Option[String] = None,
      protocolVersion: Option[String] = None
  ): SpanDataAssert = {
    val requestContext = PekkoHttpServerParsingErrorTest.requestContextAvailable
    val name = if (requestContext) method.getOrElse("HTTP") else "HTTP"
    span
      .hasName(name)
      .hasKind(SpanKind.SERVER)
      .hasNoParent()
    if (statusCode >= 500) {
      span.hasStatus(StatusData.error())
    }
    span.hasAttributesSatisfyingExactly(
      equalTo(
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        java.lang.Long.valueOf(statusCode)
      ),
      equalTo(
        ErrorAttributes.ERROR_TYPE,
        if (statusCode >= 500) statusCode.toString else null
      ),
      equalTo(
        HttpAttributes.HTTP_REQUEST_METHOD,
        if (requestContext) method.orNull else null
      ),
      equalTo(
        UrlAttributes.URL_PATH,
        if (requestContext) path.orNull else null
      ),
      equalTo(
        UrlAttributes.URL_SCHEME,
        if (requestContext) scheme.orNull else null
      ),
      equalTo(
        UrlAttributes.URL_QUERY,
        if (requestContext) query.orNull else null
      ),
      equalTo(
        NetworkAttributes.NETWORK_PROTOCOL_VERSION,
        if (requestContext) protocolVersion.orNull else null
      )
    )
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
