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
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.{HttpAttributes, UrlAttributes}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
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

/** A request that pekko-http rejects while parsing it never becomes an
  * HttpRequest and never reaches the user handler, so it is traced through the
  * parsing error handler rather than through the regular server
  * instrumentation.
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
          Http().bindAndHandleSync(handler, "localhost", 0),
          10.seconds
        )
      val port = binding.localAddress.getPort

      try {
        val response = send(
          port,
          "GET /%% HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n"
        )
        assertThat(response.head).contains("400 Bad Request")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span
                  .hasName("GET")
                  .hasKind(SpanKind.SERVER)
                  .hasNoParent()
                  .hasAttributesSatisfyingExactly(
                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                    equalTo(UrlAttributes.URL_PATH, "/%%"),
                    equalTo(
                      HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                      java.lang.Long.valueOf(400)
                    )
                  )
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** The parser is reused for every request on a keep alive connection and
    * never resets the fields the request line is read from, so a request that
    * fails must not be described with what an earlier one left behind.
    */
  @Test def requestLineIsNotCarriedOverFromTheRequestBefore(): Unit = {
    implicit val system: ActorSystem = ActorSystem(
      "parsing-error-keep-alive-test"
    )
    try {
      val handler: HttpRequest => HttpResponse = _ => HttpResponse()
      val binding =
        Await.result(
          Http().bindAndHandleSync(handler, "localhost", 0),
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
                  span
                    .hasName("POST")
                    .hasKind(SpanKind.SERVER)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(
                      equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"),
                      equalTo(UrlAttributes.URL_PATH, "/%%"),
                      equalTo(
                        HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                        java.lang.Long.valueOf(400)
                      )
                    )
                  ()
                }
              })
          }
        )
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** Most parsing failures happen after the request line, in which case the
    * target reported is the one pekko-http itself parsed and validated.
    */
  @Test def failureAfterTheRequestLineReportsTheParsedTarget(): Unit = {
    implicit val system: ActorSystem = ActorSystem("parsing-error-header-test")
    try {
      val handler: HttpRequest => HttpResponse = _ => HttpResponse()
      val binding =
        Await.result(
          Http().bindAndHandleSync(handler, "localhost", 0),
          10.seconds
        )
      val port = binding.localAddress.getPort

      try {
        // the request line parses cleanly, the failure is in the headers
        val response = send(
          port,
          "GET /valid/path?a=1 HTTP/1.1\r\nHost: localhost:" + port +
            "\r\nContent-Length: not-a-number\r\n\r\n"
        )
        assertThat(response.head).contains("400 Bad Request")

        testing.waitAndAssertTraces(new Consumer[TraceAssert] {
          override def accept(trace: TraceAssert): Unit =
            trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span
                  .hasName("GET")
                  .hasKind(SpanKind.SERVER)
                  .hasNoParent()
                  .hasAttributesSatisfyingExactly(
                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                    equalTo(UrlAttributes.URL_PATH, "/valid/path"),
                    equalTo(UrlAttributes.URL_QUERY, "a=1"),
                    equalTo(
                      HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                      java.lang.Long.valueOf(400)
                    )
                  )
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  private def send(port: Int, request: String): List[String] = {
    val socket = new Socket("localhost", port)
    try {
      val out =
        new OutputStreamWriter(
          socket.getOutputStream,
          StandardCharsets.US_ASCII
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
