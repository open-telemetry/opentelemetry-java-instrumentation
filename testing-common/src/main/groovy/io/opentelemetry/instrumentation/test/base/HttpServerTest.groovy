/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import io.opentelemetry.testing.internal.armeria.common.HttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpRequestBuilder
import spock.lang.Unroll

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP
import static java.util.Collections.singletonList
import static org.junit.Assume.assumeTrue

@Unroll
abstract class HttpServerTest<SERVER> extends InstrumentationSpecification implements HttpServerTestTrait<SERVER> {

  static final String TEST_REQUEST_HEADER = "X-Test-Request"
  static final String TEST_RESPONSE_HEADER = "X-Test-Response"

  static CapturedHttpHeaders capturedHttpHeadersForTesting() {
    CapturedHttpHeaders.create(
      singletonList(TEST_REQUEST_HEADER),
      singletonList(TEST_RESPONSE_HEADER))
  }

  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/:id/param"
      case NOT_FOUND:
        return getContextPath() + "/*"
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

  boolean testErrorBody() {
    true
  }

  boolean testException() {
    true
  }

  Class<?> expectedExceptionClass() {
    Exception
  }

  boolean testRedirect() {
    true
  }

  boolean testError() {
    true
  }

  boolean testConcurrency() {
    false
  }

  boolean verifyServerSpanEndTime() {
    return true
  }

  List<AttributeKey<?>> extraAttributes() {
    []
  }

  enum ServerEndpoint {
    SUCCESS("success", 200, "success"),
    REDIRECT("redirect", 302, "/redirected"),
    ERROR("error-status", 500, "controller error"), // "error" is a special path for some frameworks
    EXCEPTION("exception", 500, "controller exception"),
    NOT_FOUND("notFound", 404, "not found"),
    CAPTURE_HEADERS("captureHeaders", 200, "headers captured"),

    // TODO: add tests for the following cases:
    QUERY_PARAM("query?some=query", 200, "some=query"),
    // OkHttp never sends the fragment in the request, so these cases don't work.
//    FRAGMENT_PARAM("fragment#some-fragment", 200, "some-fragment"),
//    QUERY_FRAGMENT_PARAM("query/fragment?some=query#some-fragment", 200, "some=query#some-fragment"),
    PATH_PARAM("path/123/param", 200, "123"),
    AUTH_REQUIRED("authRequired", 200, null),
    LOGIN("login", 302, null),
    AUTH_ERROR("basicsecured/endpoint", 401, null),
    INDEXED_CHILD("child", 200, null),

    public static final String ID_ATTRIBUTE_NAME = "test.request.id"
    public static final String ID_PARAMETER_NAME = "id"

    private final URI uriObj
    private final String path
    final String query
    final String fragment
    final int status
    final String body

    ServerEndpoint(String uri, int status, String body) {
      this.uriObj = URI.create(uri)
      this.path = uriObj.path
      this.query = uriObj.query
      this.fragment = uriObj.fragment
      this.status = status
      this.body = body
    }

    String getPath() {
      return "/$path"
    }

    String rawPath() {
      return path
    }

    URI resolvePath(URI address) {
      return address.resolve(path)
    }

    URI resolve(URI address) {
      return address.resolve(uriObj)
    }

    URI resolveWithoutFragment(URI address) {
      def uri = resolve(address)
      return new URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, null)
    }

    /**
     * Populates custom test attributes for the {@link HttpServerTest#controller} span (which must
     * be the current span when this is called) based on URL parameters. Required for
     * {@link #INDEXED_CHILD}.
     */
    void collectSpanAttributes(UrlParameterProvider parameterProvider) {
      if (this == INDEXED_CHILD) {
        String value = parameterProvider.getParameter(ID_PARAMETER_NAME)

        if (value != null) {
          Span.current().setAttribute(ID_ATTRIBUTE_NAME, value as long)
        }
      }
    }

    private static final Map<String, ServerEndpoint> PATH_MAP = values().collectEntries { [it.path, it] }

    static ServerEndpoint forPath(String path) {
      return PATH_MAP.get(path)
    }

    // Static keyword required for Scala interop
    static interface UrlParameterProvider {
      String getParameter(String name)
    }
  }

  AggregatedHttpRequest request(ServerEndpoint uri, String method) {
    def url = uri.resolvePath(address).toString()
    // Force HTTP/1 via h1c so upgrade requests don't show up as traces
    url = url.replace("http://", "h1c://")
    if (uri.query != null) {
      url += "?${uri.query}"
    }
    return AggregatedHttpRequest.of(HttpMethod.valueOf(method), url)
  }

  static <T> T controller(ServerEndpoint endpoint, Callable<T> closure) {
    assert Span.current().getSpanContext().isValid(): "Controller should have a parent span."
    if (endpoint == NOT_FOUND) {
      return closure.call()
    }
    return runUnderTrace("controller", closure)
  }

  def "test success with #count requests"() {
    setup:
    def request = request(SUCCESS, method)
    List<AggregatedHttpResponse> responses = (1..count).collect {
      return client.execute(request).aggregate().join()
    }

    expect:
    responses.each { response ->
      assert response.status().code() == SUCCESS.status
      assert response.contentUtf8() == SUCCESS.body
    }

    and:
    assertTheTraces(count, null, null, method, SUCCESS, null, responses[0])

    where:
    method = "GET"
    count << [1, 4, 50] // make multiple requests.
  }

  def "test success with parent"() {
    setup:
    def traceId = "00000000000000000000000000000123"
    def parentId = "0000000000000456"
    def request = AggregatedHttpRequest.of(
      request(SUCCESS, method).headers().toBuilder()
        .set("traceparent", "00-" + traceId.toString() + "-" + parentId.toString() + "-01")
        .build())
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == SUCCESS.status
    response.contentUtf8() == SUCCESS.body

    and:
    assertTheTraces(1, traceId, parentId, "GET", SUCCESS, null, response)

    where:
    method = "GET"
  }

  def "test tag query string for #endpoint"() {
    setup:
    def request = request(endpoint, method)
    AggregatedHttpResponse response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == endpoint.status
    response.contentUtf8() == endpoint.body

    and:
    assertTheTraces(1, null, null, method, endpoint, null, response)

    where:
    method = "GET"
    endpoint << [SUCCESS, QUERY_PARAM]
  }

  def "test redirect"() {
    setup:
    assumeTrue(testRedirect())
    def request = request(REDIRECT, method)
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == REDIRECT.status
    response.headers().get("location") == REDIRECT.body ||
      new URI(response.headers().get("location")).normalize().toString() == "${address.resolve(REDIRECT.body)}"

    and:
    assertTheTraces(1, null, null, method, REDIRECT, null, response)

    where:
    method = "GET"
  }

  def "test error"() {
    setup:
    assumeTrue(testError())
    def request = request(ERROR, method)
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == ERROR.status
    if (testErrorBody()) {
      response.contentUtf8() == ERROR.body
    }

    and:
    assertTheTraces(1, null, null, method, ERROR, null, response)

    where:
    method = "GET"
  }

  def "test exception"() {
    setup:
    assumeTrue(testException())
    def request = request(EXCEPTION, method)
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == EXCEPTION.status

    and:
    assertTheTraces(1, null, null, method, EXCEPTION, EXCEPTION.body, response)

    where:
    method = "GET"
  }

  def "test notFound"() {
    setup:
    assumeTrue(testNotFound())
    def request = request(NOT_FOUND, method)
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == NOT_FOUND.status

    and:
    assertTheTraces(1, null, null, method, NOT_FOUND, null, response)

    where:
    method = "GET"
  }

  def "test path param"() {
    setup:
    assumeTrue(testPathParam())
    def request = request(PATH_PARAM, method)
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == PATH_PARAM.status
    response.contentUtf8() == PATH_PARAM.body

    and:
    assertTheTraces(1, null, null, method, PATH_PARAM, null, response)

    where:
    method = "GET"
  }

  def "test captured HTTP headers"() {
    setup:
    assumeTrue(testCapturedHttpHeaders())

    def request = AggregatedHttpRequest.of(request(CAPTURE_HEADERS, "GET").headers()
      .toBuilder()
      .add(TEST_REQUEST_HEADER, "test")
      .build())
    def response = client.execute(request).aggregate().join()

    expect:
    response.status().code() == CAPTURE_HEADERS.status
    response.contentUtf8() == CAPTURE_HEADERS.body

    and:
    assertTheTraces(1, null, null, "GET", CAPTURE_HEADERS, null, response)
  }

  /*
  This test fires a bunch of parallel request to the fixed backend endpoint.
  That endpoint is supposed to create a new child span in the context of the SERVER span.
  That child span is expected to have an attribute called "test.request.id".
  The value of that attribute should be the value of request's parameter called "id".

  This test then asserts that there is the correct number of traces (one per request executed)
  and that each trace has exactly three spans and both first and the last spans have "test.request.id"
  attribute with equal value. Server span is not going to have that attribute because it is not
  under the control of this test.

  This way we verify that child span created by the server actually corresponds to the client request.
   */

  def "high concurrency test"() {
    setup:
    assumeTrue(testConcurrency())
    int count = 100
    def endpoint = INDEXED_CHILD

    def latch = new CountDownLatch(count)

    def propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
    def setter = { HttpRequestBuilder carrier, String name, String value ->
      carrier.header(name, value)
    }

    when:
    count.times { index ->
      HttpRequestBuilder request = HttpRequest.builder()
      // Force HTTP/1 via h1c so upgrade requests don't show up as traces
        .get(endpoint.resolvePath(address).toString().replace("http://", "h1c://"))
        .queryParam(ServerEndpoint.ID_PARAMETER_NAME, "$index")
      runUnderTrace("client " + index) {
        Span.current().setAttribute(ServerEndpoint.ID_ATTRIBUTE_NAME, index)
        propagator.inject(Context.current(), request, setter)
        client.execute(request.build()).aggregate().thenRun {
          latch.countDown()
        }
      }
    }
    latch.await()

    then:
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
          indexedServerSpan(it, span(0), requestId)

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

  void assertTheTraces(int size, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS, String errorMessage = null, AggregatedHttpResponse response = null) {
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
            controllerSpan(it, spanIndex++, span(controllerSpanIndex), errorMessage, expectedExceptionClass())
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

  void controllerSpan(TraceAssert trace, int index, Object parent, String errorMessage = null, Class exceptionClass = Exception) {
    trace.span(index) {
      name "controller"
      if (errorMessage) {
        status StatusCode.ERROR
        errorEvent(exceptionClass, errorMessage)
      }
      childOf((SpanData) parent)
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

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", Long responseContentLength = null, ServerEndpoint endpoint = SUCCESS) {
    def extraAttributes = extraAttributes()
    trace.span(index) {
      name expectedServerSpanName(endpoint)
      kind SpanKind.SERVER // can't use static import because of SERVER type parameter
      if (endpoint.status >= 500) {
        status StatusCode.ERROR
      }
      if (parentID != null) {
        traceId traceID
        parentSpanId parentID
      } else {
        hasNoParent()
      }
      if (endpoint == EXCEPTION && hasExceptionOnServerSpan(endpoint)) {
        event(0) {
          eventName(SemanticAttributes.EXCEPTION_EVENT_NAME)
          attributes {
            "${SemanticAttributes.EXCEPTION_TYPE.key}" { it == null || it == expectedExceptionClass().name }
            "${SemanticAttributes.EXCEPTION_MESSAGE.key}" { it == null || it == endpoint.body }
            "${SemanticAttributes.EXCEPTION_STACKTRACE.key}" { it == null || it instanceof String }
          }
        }
      }
      attributes {
        if (extraAttributes.contains(SemanticAttributes.NET_TRANSPORT)) {
          "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
        }
        // net.peer.name resolves to "127.0.0.1" on windows which is same as net.peer.ip so then not captured
        "${SemanticAttributes.NET_PEER_NAME.key}" { it == null || it == address.host }
        "${SemanticAttributes.NET_PEER_PORT.key}" { it == null || it instanceof Long }
        "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == peerIp(endpoint) } // Optional

        "${SemanticAttributes.HTTP_CLIENT_IP.key}" { it == null || it == TEST_CLIENT_IP }
        "${SemanticAttributes.HTTP_METHOD.key}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key}" endpoint.status
        "${SemanticAttributes.HTTP_FLAVOR.key}" { it == "1.1" || it == "2.0" }
        "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT

        "${SemanticAttributes.HTTP_HOST}" { it == "localhost" || it == "localhost:${port}" }
        // TODO netty does not set http.scheme - refactor HTTP server tests so that it's possible to specify extracted attributes, like in HTTP client tests
        "${SemanticAttributes.HTTP_SCHEME}" { it == "http" || it == null }
        "${SemanticAttributes.HTTP_TARGET}" endpoint.resolvePath(address).getPath() + "${endpoint == QUERY_PARAM ? "?${endpoint.body}" : ""}"

        if (extraAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)) {
          "${SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH}" Long
        } else {
          "${SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH}" { it == null || it instanceof Long }
          // Optional
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)) {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" Long
        } else {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" { it == null || it instanceof Long }
          // Optional
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_ROUTE)) {
          // TODO(anuraaga): Revisit this when applying instrumenters to more libraries, Armeria
          // currently reports '/*' which is a fallback route.
          "${SemanticAttributes.HTTP_ROUTE}" String
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_SERVER_NAME)) {
          "${SemanticAttributes.HTTP_SERVER_NAME}" String
        }
        if (endpoint == CAPTURE_HEADERS) {
          "http.request.header.x_test_request" { it == ["test"] }
          "http.response.header.x_test_response" { it == ["test"] }
        }
      }
    }
  }

  void indexedServerSpan(TraceAssert trace, Object parent, int requestId) {
    def extraAttributes = extraAttributes()
    ServerEndpoint endpoint = INDEXED_CHILD
    trace.span(1) {
      name expectedServerSpanName(endpoint)
      kind SpanKind.SERVER // can't use static import because of SERVER type parameter
      childOf((SpanData) parent)
      attributes {
        if (extraAttributes.contains(SemanticAttributes.NET_TRANSPORT)) {
          "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
        }
        // net.peer.name resolves to "127.0.0.1" on windows which is same as net.peer.ip so then not captured
        "${SemanticAttributes.NET_PEER_NAME.key}" { (it == null || it == address.host) }
        "${SemanticAttributes.NET_PEER_PORT.key}" { it == null || it instanceof Long }
        "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == peerIp(endpoint) } // Optional

        "${SemanticAttributes.HTTP_CLIENT_IP.key}" { it == null || it == TEST_CLIENT_IP }
        "${SemanticAttributes.HTTP_METHOD.key}" "GET"
        "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
        "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT

        "${SemanticAttributes.HTTP_HOST}" { it == "localhost" || it == "localhost:${port}" }
        // TODO netty does not set http.scheme - refactor HTTP server tests so that it's possible to specify extracted attributes, like in HTTP client tests
        "${SemanticAttributes.HTTP_SCHEME}" { it == "http" || it == null }
        "${SemanticAttributes.HTTP_TARGET}" endpoint.resolvePath(address).getPath() + "?id=$requestId"

        if (extraAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)) {
          "${SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH}" Long
        } else {
          "${SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH}" { it == null || it instanceof Long }
          // Optional
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)) {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" Long
        } else {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" { it == null || it instanceof Long }
          // Optional
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_ROUTE)) {
          // TODO(anuraaga): Revisit this when applying instrumenters to more libraries, Armeria
          // currently reports '/*' which is a fallback route.
          "${SemanticAttributes.HTTP_ROUTE}" String
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_SERVER_NAME)) {
          "${SemanticAttributes.HTTP_SERVER_NAME}" String
        }
      }
    }
  }

  void indexedControllerSpan(TraceAssert trace, int index, Object parent, int requestId) {
    trace.span(index) {
      name "controller"
      childOf((SpanData) parent)
      attributes {
        "${ServerEndpoint.ID_ATTRIBUTE_NAME}" requestId
      }
    }
  }
}
