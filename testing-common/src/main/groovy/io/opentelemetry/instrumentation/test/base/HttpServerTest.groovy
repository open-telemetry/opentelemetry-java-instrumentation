/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.Callable

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import static org.junit.jupiter.api.Assumptions.assumeTrue

@Unroll
abstract class HttpServerTest<SERVER> extends InstrumentationSpecification implements HttpServerTestTrait<SERVER> {

  def setupSpec() {
    setupServer()
    junitTest.setupOptions()
    junitTest.setTesting(testRunner(), client, port, address)
  }

  def cleanupSpec() {
    cleanupServer()
  }

  String expectedServerSpanName(ServerEndpoint endpoint, String method) {
    def route = expectedHttpRoute(endpoint)
    return route == null ? "HTTP $method" : route
  }

  String expectedHttpRoute(ServerEndpoint endpoint) {
    // no need to compute route if we're not expecting it
    if (!httpAttributes(endpoint).contains(SemanticAttributes.HTTP_ROUTE)) {
      return null
    }

    switch (endpoint) {
      case NOT_FOUND:
        return null
      case PATH_PARAM:
        return getContextPath() + "/path/:id/param"
      default:
        return endpoint.resolvePath(address).path
    }
  }

  String getContextPath() {
    return ""
  }

  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    false
  }

  boolean hasHandlerAsControllerParentSpan(ServerEndpoint endpoint) {
    true
  }

  boolean hasExceptionOnServerSpan(ServerEndpoint endpoint) {
    !hasHandlerSpan(endpoint)
  }

  boolean hasRenderSpan(ServerEndpoint endpoint) {
    false
  }

  boolean hasResponseSpan(ServerEndpoint endpoint) {
    false
  }

  int getErrorPageSpansCount(ServerEndpoint endpoint) {
    1
  }

  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    false
  }

  String peerIp(ServerEndpoint endpoint) {
    "127.0.0.1"
  }

  boolean testNotFound() {
    true
  }

  boolean testPathParam() {
    false
  }

  boolean testCapturedHttpHeaders() {
    true
  }

  boolean testCapturedRequestParameters() {
    false
  }

  boolean testErrorBody() {
    true
  }

  boolean testException() {
    true
  }

  Throwable expectedException() {
    new Exception(EXCEPTION.body)
  }

  boolean testRedirect() {
    true
  }

  boolean testError() {
    true
  }

  boolean verifyServerSpanEndTime() {
    return true
  }

  /** A list of additional HTTP server span attributes extracted by the instrumentation per URI. */
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    [
      SemanticAttributes.HTTP_ROUTE,
      SemanticAttributes.NET_TRANSPORT,
      SemanticAttributes.NET_PEER_PORT
    ] as Set
  }


  final String resolveAddress(ServerEndpoint endpoint) {
    return junitTest.resolveAddress(endpoint)
  }

  AggregatedHttpRequest request(ServerEndpoint uri, String method) {
    return AggregatedHttpRequest.of(HttpMethod.valueOf(method), resolveAddress(uri))
  }

  static <T> T controller(ServerEndpoint endpoint, Callable<T> closure) {
    assert Span.current().getSpanContext().isValid(): "Controller should have a parent span."
    if (endpoint == NOT_FOUND) {
      return closure.call()
    }
    return GlobalTraceUtil.runWithSpan("controller") {
      closure.call()
    }
  }

  @Shared
  def junitTest = new AbstractHttpServerTest<Void>() {
    @Override
    protected Void setupServer() {
      return null
    }

    @Override
    protected void stopServer(Void o) {
    }

    @Override
    protected void configure(HttpServerTestOptions options) {
      options.expectedServerSpanNameMapper = { endpoint, method ->
        HttpServerTest.this.expectedServerSpanName(endpoint, method)
      }
      options.expectedHttpRoute = { endpoint ->
        HttpServerTest.this.expectedHttpRoute(endpoint)
      }
      options.contextPath = getContextPath()
      options.httpAttributes = { endpoint ->
        HttpServerTest.this.httpAttributes(endpoint)
      }
      options.expectedException = expectedException()
      options.hasExceptionOnServerSpan = { endpoint ->
        HttpServerTest.this.hasExceptionOnServerSpan(endpoint)
      }

      options.testRedirect = testRedirect()
      options.testError = testError()
      options.testErrorBody = testErrorBody()
      options.testException = testException()
      options.testNotFound = testNotFound()
      options.testPathParam = testPathParam()
      options.testCaptureHttpHeaders = testCapturedHttpHeaders()
      options.testCaptureRequestParameters = testCapturedRequestParameters()
    }

    // Override trace assertion method. We can call java assertions from groovy but not the other
    // way around. As we have a bunch of groovy tests that do custom assertions we need to duplicate
    // the main trace assertion method to groovy to be able to call these assertions.
    @Override
    void assertTheTraces(
        int size,
        String traceId,
        String parentId,
        String method,
        ServerEndpoint endpoint,
        AggregatedHttpResponse response) {
      HttpServerTest.this.assertTheTraces(size, traceId, parentId, method, endpoint, response)
    }

    @Override
    void assertHighConcurrency(int count) {
      HttpServerTest.this.assertHighConcurrency(count)
    }
  }

  def "test success with #count requests"() {
    expect:
    junitTest.successfulGetRequest(count)

    where:
    count << [1, 4, 50] // make multiple requests.
  }

  def "test success with parent"() {
    expect:
    junitTest.successfulGetRequestWithParent()
  }

  // make sure that TextMapGetters are not case-sensitive
  def "test success with uppercase TRACEPARENT header"() {
    expect:
    junitTest.tracingHeaderIsCaseInsensitive()
  }

  def "test tag query string for #endpoint"() {
    expect:
    junitTest.requestWithQueryString(endpoint)

    where:
    endpoint << [SUCCESS, QUERY_PARAM]
  }

  def "test redirect"() {
    assumeTrue(testRedirect())
    expect:
    junitTest.requestWithRedirect()
  }

  def "test error"() {
    assumeTrue(testError())
    expect:
    junitTest.requestWithError()
  }

  def "test exception"() {
    assumeTrue(testException())
    expect:
    junitTest.requestWithException()
  }

  def "test not found"() {
    assumeTrue(testNotFound())
    expect:
    junitTest.requestForNotFound()
  }

  def "test path param"() {
    assumeTrue(testPathParam())
    expect:
    junitTest.requestWithPathParameter()
  }

  def "test captured HTTP headers"() {
    assumeTrue(testCapturedHttpHeaders())
    expect:
    junitTest.captureHttpHeaders()
  }

  def "test captured request parameters"() {
    assumeTrue(testCapturedRequestParameters())
    expect:
    junitTest.captureRequestParameters()
  }

  def "high concurrency test"() {
    expect:
    junitTest.highConcurrency()
  }

  void assertHighConcurrency(int count) {
    def endpoint = INDEXED_CHILD
    assertTraces(count) {
      (0..count - 1).each {
        trace(it, hasHandlerSpan(endpoint) ? 4 : 3) {
          def rootSpan = it.span(0)
          //Traces can be in arbitrary order, let us find out the request id of the current one
          def requestId = Integer.parseInt(rootSpan.name.substring("client ".length()))

          span(0) {
            name "client " + requestId
            kind SpanKind.INTERNAL
            hasNoParent()
            attributes {
              "${ServerEndpoint.ID_ATTRIBUTE_NAME}" requestId
            }
          }
          indexedServerSpan(it, 1, span(0), requestId)

          def controllerSpanIndex = 2

          if (hasHandlerSpan(endpoint)) {
            handlerSpan(it, 2, span(1), "GET", endpoint)
            controllerSpanIndex++
          }

          def controllerParentSpanIndex = controllerSpanIndex - (hasHandlerAsControllerParentSpan(endpoint) ? 1 : 2)
          indexedControllerSpan(it, controllerSpanIndex, span(controllerParentSpanIndex), requestId)
        }
      }
    }
  }

  //FIXME: add tests for POST with large/chunked data

  void assertTheTraces(int size, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS, AggregatedHttpResponse response = null) {
    def spanCount = 1 // server span
    if (hasResponseSpan(endpoint)) {
      spanCount++
    }
    if (hasHandlerSpan(endpoint)) {
      spanCount++
    }
    if (endpoint != NOT_FOUND) {
      spanCount++ // controller span
      if (hasRenderSpan(endpoint)) {
        spanCount++
      }
    }
    if (hasErrorPageSpans(endpoint)) {
      spanCount += getErrorPageSpansCount(endpoint)
    }
    assertTraces(size) {
      (0..size - 1).each {
        trace(it, spanCount) {
          def spanIndex = 0
          if (verifyServerSpanEndTime() && spanCount > 1) {
            (1..spanCount - 1).each { index ->
              assert it.span(0).endEpochNanos - it.span(index).endEpochNanos >= 0
            }
          }
          serverSpan(it, spanIndex++, traceID, parentID, method, response?.content()?.length(), endpoint)
          if (hasHandlerSpan(endpoint)) {
            handlerSpan(it, spanIndex++, span(0), method, endpoint)
          }
          if (endpoint != NOT_FOUND) {
            def controllerSpanIndex = 0
            if (hasHandlerSpan(endpoint) && hasHandlerAsControllerParentSpan(endpoint)) {
              controllerSpanIndex++
            }
            controllerSpan(it, spanIndex++, span(controllerSpanIndex), endpoint == EXCEPTION ? expectedException() : null)
            if (hasRenderSpan(endpoint)) {
              renderSpan(it, spanIndex++, span(0), method, endpoint)
            }
          }
          if (hasResponseSpan(endpoint)) {
            responseSpan(it, spanIndex, span(spanIndex - 1), span(0), method, endpoint)
            spanIndex++
          }
          if (hasErrorPageSpans(endpoint)) {
            errorPageSpans(it, spanIndex, span(0), method, endpoint)
          }
        }
      }
    }
  }

  void controllerSpan(TraceAssert trace, int index, Object parent, Throwable expectedException) {
    trace.assertedIndexes.add(index)
    def spanData = trace.span(index)
    def assertion = junitTest.assertControllerSpan(OpenTelemetryAssertions.assertThat(spanData), expectedException)
    if (parent == null) {
      assertion.hasParentSpanId(SpanId.invalid)
    } else {
      assertion.hasParentSpanId(((SpanData) parent).spanId)
    }
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("handlerSpan not implemented in " + getClass().name)
  }

  void renderSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("renderSpan not implemented in " + getClass().name)
  }

  void responseSpan(TraceAssert trace, int index, Object controllerSpan, Object handlerSpan, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    responseSpan(trace, index, controllerSpan, method, endpoint)
  }

  void responseSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("responseSpan not implemented in " + getClass().name)
  }

  void errorPageSpans(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    throw new UnsupportedOperationException("errorPageSpans not implemented in " + getClass().name)
  }

  void redirectSpan(TraceAssert trace, int index, Object parent) {
    trace.span(index) {
      name ~/\.sendRedirect$/
      kind SpanKind.INTERNAL
      childOf((SpanData) parent)
    }
  }

  void sendErrorSpan(TraceAssert trace, int index, Object parent) {
    trace.span(index) {
      name ~/\.sendError$/
      kind SpanKind.INTERNAL
      childOf((SpanData) parent)
    }
  }

  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", Long responseContentLength = null, ServerEndpoint endpoint = SUCCESS) {
    trace.assertedIndexes.add(index)
    def spanData = trace.span(index)
    def assertion = junitTest.assertServerSpan(OpenTelemetryAssertions.assertThat(spanData), method, endpoint)
    if (parentID == null) {
      assertion.hasParentSpanId(SpanId.invalid)
    } else {
      assertion.hasParentSpanId(parentID)
      assertion.hasTraceId(traceID)
    }
  }

  void indexedServerSpan(TraceAssert trace, int index, Object parent, int requestId) {
    trace.assertedIndexes.add(index)
    def spanData = trace.span(index)
    def assertion = junitTest.assertIndexedServerSpan(OpenTelemetryAssertions.assertThat(spanData), requestId)
    if (parent == null) {
      assertion.hasParentSpanId(SpanId.invalid)
    } else {
      assertion.hasParentSpanId(((SpanData) parent).spanId)
    }
  }

  void indexedControllerSpan(TraceAssert trace, int index, Object parent, int requestId) {
    trace.assertedIndexes.add(index)
    def spanData = trace.span(index)
    def assertion = junitTest.assertIndexedControllerSpan(OpenTelemetryAssertions.assertThat(spanData), requestId)
    if (parent == null) {
      assertion.hasParentSpanId(SpanId.invalid)
    } else {
      assertion.hasParentSpanId(((SpanData) parent).spanId)
    }
  }
}
