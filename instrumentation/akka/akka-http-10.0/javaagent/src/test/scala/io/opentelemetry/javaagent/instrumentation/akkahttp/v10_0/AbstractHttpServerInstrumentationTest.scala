/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.{
  LongAssertConsumer,
  equalTo,
  satisfies
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS
import io.opentelemetry.semconv.HttpAttributes.{
  HTTP_REQUEST_METHOD,
  HTTP_RESPONSE_STATUS_CODE,
  HTTP_ROUTE
}
import io.opentelemetry.semconv.NetworkAttributes.{
  NETWORK_PEER_ADDRESS,
  NETWORK_PEER_PORT,
  NETWORK_PROTOCOL_VERSION
}
import io.opentelemetry.semconv.ServerAttributes.{SERVER_ADDRESS, SERVER_PORT}
import io.opentelemetry.semconv.UrlAttributes.{URL_PATH, URL_SCHEME}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AbstractLongAssert

import java.net.{InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets.US_ASCII
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.semconv.HttpAttributes

import java.util
import java.util.function.{Consumer, Function, Predicate}

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
    // instrumentation does not create a span at all
    options.disableTestNonStandardHttpMethod
  }

  protected def configureRouteServer(options: HttpServerTestOptions): Unit = {
    options.setTestException(false)
    options.setTestPathParam(true)
    options.setHttpAttributes(
      new Function[ServerEndpoint, util.Set[AttributeKey[_]]] {
        override def apply(v1: ServerEndpoint): util.Set[AttributeKey[_]] =
          HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES
      }
    )
    options.setExpectedHttpRoute(
      new java.util.function.BiFunction[ServerEndpoint, String, String] {
        override def apply(
            endpoint: ServerEndpoint,
            method: String
        ): String =
          if (endpoint eq ServerEndpoint.PATH_PARAM) "/path/*/param"
          else expectedHttpRoute(endpoint, method)
      }
    )
  }

  protected def assertClientAddressWithoutForwardingHeader(): Unit = {
    val socket = new Socket()
    socket.connect(new InetSocketAddress("localhost", port))
    socket.setSoTimeout(10000)
    val response =
      try {
        val requestPath = "/" + SUCCESS.rawPath()
        val request =
          s"GET $requestPath HTTP/1.1\r\nHost: localhost:$port\r\nConnection: close\r\n\r\n"
        socket.getOutputStream.write(request.getBytes(US_ASCII))
        socket.getOutputStream.flush()
        scala.io.Source
          .fromInputStream(socket.getInputStream, US_ASCII.name())
          .mkString
      } finally {
        socket.close()
      }
    assertThat(response).contains("200 OK")
    assertThat(response).contains(SUCCESS.getBody)

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span
                .hasName("GET /success")
                .hasKind(SpanKind.SERVER)
                .hasNoParent
                .hasAttributesSatisfyingExactly(
                  equalTo(URL_SCHEME, "http"),
                  equalTo(URL_PATH, "/success"),
                  equalTo(HTTP_ROUTE, "/success"),
                  equalTo(HTTP_REQUEST_METHOD, "GET"),
                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                  equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                  equalTo(SERVER_ADDRESS, "localhost"),
                  equalTo(SERVER_PORT, port),
                  equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                  satisfies(
                    NETWORK_PEER_PORT,
                    new LongAssertConsumer {
                      override def accept(value: AbstractLongAssert[_]): Unit =
                        value.isNotEqualTo(port)
                    }
                  )
                )
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span.hasName("controller")
          }
        )
    })
  }
}
