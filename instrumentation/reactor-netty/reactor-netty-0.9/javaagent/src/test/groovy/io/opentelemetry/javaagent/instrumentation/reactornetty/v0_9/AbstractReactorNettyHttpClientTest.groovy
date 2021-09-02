/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.sdk.trace.data.SpanData
import reactor.netty.http.client.HttpClient

import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT

abstract class AbstractReactorNettyHttpClientTest extends HttpClientTest<HttpClient.ResponseReceiver> implements AgentTestTrait {

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  String userAgent() {
    return "ReactorNetty"
  }

  @Override
  HttpClient.ResponseReceiver buildRequest(String method, URI uri, Map<String, String> headers) {
    return createHttpClient()
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      .baseUrl(resolveAddress("").toString())
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
  }

  @Override
  int sendRequest(HttpClient.ResponseReceiver request, String method, URI uri, Map<String, String> headers) {
    return request.responseSingle { resp, content ->
      // Make sure to consume content since that's when we close the span.
      content.map {
        resp
      }
    }.block().status().code()
  }

  @Override
  void sendRequestWithCallback(HttpClient.ResponseReceiver request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    request.responseSingle { resp, content ->
      // Make sure to consume content since that's when we close the span.
      content.map { resp }
    }.subscribe({
      requestResult.complete(it.status().code())
    }, { throwable ->
      requestResult.complete(throwable)
    })
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  Throwable clientSpanError(URI uri, Throwable exception) {
    if (exception.class.getName().endsWith("ReactiveException")) {
      switch (uri.toString()) {
        case "http://localhost:61/": // unopened port
        case "https://192.0.2.1/": // non routable address
          exception = exception.getCause()
      }
    }
    return exception
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return []
    }
    return super.httpAttributes(uri)
  }

  abstract HttpClient createHttpClient()

  def "should expose context to http client callbacks"() {
    given:
    def onRequestSpan = new AtomicReference<Span>()
    def afterRequestSpan = new AtomicReference<Span>()
    def onResponseSpan = new AtomicReference<Span>()
    def afterResponseSpan = new AtomicReference<Span>()
    def latch = new CountDownLatch(1)

    def httpClient = createHttpClient()
      .doOnRequest({ rq, con -> onRequestSpan.set(Span.current()) })
      .doAfterRequest({ rq, con -> afterRequestSpan.set(Span.current()) })
      .doOnResponse({ rs, con -> onResponseSpan.set(Span.current()) })
      .doAfterResponse({ rs, con ->
        afterResponseSpan.set(Span.current())
        latch.countDown()
      })

    when:
    runWithSpan("parent") {
      httpClient.baseUrl(resolveAddress("").toString())
        .get()
        .uri("/success")
        .responseSingle { resp, content ->
          // Make sure to consume content since that's when we close the span.
          content.map { resp }
        }
        .block()
    }
    latch.await()

    then:
    assertTraces(1) {
      trace(0, 3) {
        def parentSpan = span(0)
        def nettyClientSpan = span(1)

        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        clientSpan(it, 1, parentSpan, "GET", resolveAddress("/success"))
        serverSpan(it, 2, nettyClientSpan)

        assertSameSpan(parentSpan, onRequestSpan)
        assertSameSpan(nettyClientSpan, afterRequestSpan)
        assertSameSpan(nettyClientSpan, onResponseSpan)
        assertSameSpan(parentSpan, afterResponseSpan)
      }
    }
  }

  def "should expose context to http request error callback"() {
    given:
    def onRequestErrorSpan = new AtomicReference<Span>()

    def httpClient = createHttpClient()
      .doOnRequestError({ rq, err -> onRequestErrorSpan.set(Span.current()) })

    when:
    runWithSpan("parent") {
      httpClient.get()
        .uri("http://localhost:$UNUSABLE_PORT/")
        .responseSingle { resp, content ->
          // Make sure to consume content since that's when we close the span.
          content.map { resp }
        }
        .block()
    }

    then:
    def ex = thrown(Exception)

    assertTraces(1) {
      trace(0, 2) {
        def parentSpan = span(0)

        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
          status StatusCode.ERROR
          errorEvent(ex.class, ex.message)
        }
        span(1) {
          def actualException = ex.cause
          kind SpanKind.CLIENT
          childOf parentSpan
          status StatusCode.ERROR
          errorEvent(actualException.class, actualException.message)
        }

        assertSameSpan(parentSpan, onRequestErrorSpan)
      }
    }
  }

  private static void assertSameSpan(SpanData expected, AtomicReference<Span> actual) {
    def expectedSpanContext = expected.spanContext
    def actualSpanContext = actual.get().spanContext
    assert expectedSpanContext.traceId == actualSpanContext.traceId
    assert expectedSpanContext.spanId == actualSpanContext.spanId
  }
}
