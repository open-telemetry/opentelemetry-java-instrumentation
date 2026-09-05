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
import io.opentelemetry.semconv.HttpAttributes
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
  * instrumentation. Nothing of the rejected request is recovered, so the span
  * carries the response status and reports the method as unknown.
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
                assertParsingErrorSpan(span, 400)
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
    * side by side.
    */
  @Test def rejectionAfterHandledRequestIsTraced(): Unit = {
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
                  assertParsingErrorSpan(span, 400)
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
    */
  @Test def connectRequestIsTraced(): Unit = {
    implicit val system: ActorSystem = ActorSystem("parsing-error-connect-test")
    try {
      val handler: HttpRequest => HttpResponse = _ => HttpResponse()
      val binding =
        Await.result(
          Http().bindAndHandleSync(handler, "localhost", 0),
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
                assertParsingErrorSpan(span, 400)
                ()
              }
            })
        })
      } finally Await.result(binding.unbind(), 10.seconds)
    } finally Await.result(system.terminate(), 10.seconds)
  }

  /** Nothing of the rejected request is known, so every one of these spans
    * looks the same apart from the status the rejection was answered with.
    */
  private def assertParsingErrorSpan(
      span: SpanDataAssert,
      statusCode: Long
  ): SpanDataAssert =
    span
      .hasName("HTTP")
      .hasKind(SpanKind.SERVER)
      .hasNoParent()
      .hasAttributesSatisfyingExactly(
        equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "_OTHER"),
        equalTo(
          HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
          java.lang.Long.valueOf(statusCode)
        )
      )

  private def send(port: Int, request: String): List[String] = {
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
