/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.Callable
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import spock.lang.Unroll

@Unroll
abstract class HttpServerTest<SERVER> extends InstrumentationSpecification implements HttpServerTestTrait<SERVER> {

  String expectedServerSpanName(ServerEndpoint endpoint) {
    return endpoint == PATH_PARAM ? getContextPath() + "/path/:id/param" : endpoint.resolvePath(address).path
  }

  String getContextPath() {
    return ""
  }

  boolean hasHandlerSpan() {
    false
  }

  boolean hasRenderSpan(ServerEndpoint endpoint) {
    false
  }

  boolean hasResponseSpan(ServerEndpoint endpoint) {
    false
  }

  boolean hasForwardSpan() {
    false
  }

  boolean hasIncludeSpan() {
    false
  }

  int getErrorPageSpansCount(ServerEndpoint endpoint) {
    1
  }

  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    false
  }

  boolean testNotFound() {
    true
  }

  boolean testPathParam() {
    false
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

  enum ServerEndpoint {
    SUCCESS("success", 200, "success"),
    REDIRECT("redirect", 302, "/redirected"),
    ERROR("error-status", 500, "controller error"), // "error" is a special path for some frameworks
    EXCEPTION("exception", 500, "controller exception"),
    NOT_FOUND("notFound", 404, "not found"),

    // TODO: add tests for the following cases:
    QUERY_PARAM("query?some=query", 200, "some=query"),
    // OkHttp never sends the fragment in the request, so these cases don't work.
//    FRAGMENT_PARAM("fragment#some-fragment", 200, "some-fragment"),
//    QUERY_FRAGMENT_PARAM("query/fragment?some=query#some-fragment", 200, "some=query#some-fragment"),
    PATH_PARAM("path/123/param", 200, "123"),
    AUTH_REQUIRED("authRequired", 200, null),
    LOGIN("login", 302, null),
    AUTH_ERROR("basicsecured/endpoint", 401, null)

    private final URI uriObj
    private final String path
    final String query
    final String fragment
    final int status
    final String body
    final Boolean errored

    ServerEndpoint(String uri, int status, String body) {
      this.uriObj = URI.create(uri)
      this.path = uriObj.path
      this.query = uriObj.query
      this.fragment = uriObj.fragment
      this.status = status
      this.body = body
      this.errored = status >= 400
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

    private static final Map<String, ServerEndpoint> PATH_MAP = values().collectEntries { [it.path, it] }

    static ServerEndpoint forPath(String path) {
      return PATH_MAP.get(path)
    }
  }

  Request.Builder request(ServerEndpoint uri, String method, RequestBody body) {
    def url = HttpUrl.get(uri.resolvePath(address)).newBuilder()
      .query(uri.query)
      .fragment(uri.fragment)
      .build()
    return request(url, method, body)
  }

  Request.Builder request(HttpUrl url, String method, RequestBody body) {
    return new Request.Builder()
      .url(url)
      .method(method, body)
      .header("User-Agent", TEST_USER_AGENT)
      .header("X-Forwarded-For", TEST_CLIENT_IP)
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
    def request = request(SUCCESS, method, body).build()
    List<Response> responses = (1..count).collect {
      return client.newCall(request).execute()
    }

    expect:
    responses.each { response ->
      assert response.code() == SUCCESS.status
      assert response.body().string() == SUCCESS.body
    }

    and:
    assertTheTraces(count, null, null, method, SUCCESS, null, responses[0])

    where:
    method = "GET"
    body = null
    count << [1, 4, 50] // make multiple requests.
  }

  def "test success with parent"() {
    setup:
    def traceId = "00000000000000000000000000000123"
    def parentId = "0000000000000456"
    def request = request(SUCCESS, method, body)
      .header("traceparent", "00-" + traceId.toString() + "-" + parentId.toString() + "-01")
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status
    response.body().string() == SUCCESS.body

    and:
    assertTheTraces(1, traceId, parentId, "GET", SUCCESS, null, response)

    where:
    method = "GET"
    body = null
  }

  def "test tag query string for #endpoint"() {
    setup:
    def request = request(endpoint, method, body).build()
    Response response = client.newCall(request).execute()

    expect:
    response.code() == endpoint.status
    response.body().string() == endpoint.body

    and:
    assertTheTraces(1, null, null, method, endpoint, null, response)

    where:
    method = "GET"
    body = null
    endpoint << [SUCCESS, QUERY_PARAM]
  }

  def "test redirect"() {
    setup:
    assumeTrue(testRedirect())
    def request = request(REDIRECT, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == REDIRECT.status
    response.header("location") == REDIRECT.body ||
      response.header("location") == "${address.resolve(REDIRECT.body)}"

    and:
    assertTheTraces(1, null, null, method, REDIRECT, null, response)

    where:
    method = "GET"
    body = null
  }

  def "test error"() {
    setup:
    assumeTrue(testError())
    def request = request(ERROR, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == ERROR.status
    if (testErrorBody()) {
      response.body().string() == ERROR.body
    }

    and:
    assertTheTraces(1, null, null, method, ERROR, null, response)

    where:
    method = "GET"
    body = null
  }

  def "test exception"() {
    setup:
    assumeTrue(testException())
    def request = request(EXCEPTION, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == EXCEPTION.status

    and:
    assertTheTraces(1, null, null, method, EXCEPTION, EXCEPTION.body, response)

    where:
    method = "GET"
    body = null
  }

  def "test notFound"() {
    setup:
    assumeTrue(testNotFound())
    def request = request(NOT_FOUND, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == NOT_FOUND.status

    and:
    assertTheTraces(1, null, null, method, NOT_FOUND, null, response)

    where:
    method = "GET"
    body = null
  }

  def "test path param"() {
    setup:
    assumeTrue(testPathParam())
    def request = request(PATH_PARAM, method, body).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == PATH_PARAM.status
    response.body().string() == PATH_PARAM.body

    and:
    assertTheTraces(1, null, null, method, PATH_PARAM, null, response)

    where:
    method = "GET"
    body = null
  }

  //FIXME: add tests for POST with large/chunked data

  void assertTheTraces(int size, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS, String errorMessage = null, Response response = null) {
    def spanCount = 1 // server span
    if (hasHandlerSpan()) {
      spanCount++
    }
    if (hasResponseSpan(endpoint)) {
      spanCount++
    }
    if (endpoint != NOT_FOUND) {
      spanCount++ // controller span
      if (hasRenderSpan(endpoint)) {
        spanCount++
      }
      if (hasForwardSpan()) {
        spanCount++
      }
      if (hasIncludeSpan()) {
        spanCount++
      }
      if (hasErrorPageSpans(endpoint)) {
        spanCount += getErrorPageSpansCount(endpoint)
      }
    }
    assertTraces(size) {
      (0..size - 1).each {
        trace(it, spanCount) {
          def spanIndex = 0
          serverSpan(it, spanIndex++, traceID, parentID, method, response?.body()?.contentLength(), endpoint)
          if (hasHandlerSpan()) {
            handlerSpan(it, spanIndex++, span(0), method, endpoint)
          }
          if (endpoint != NOT_FOUND) {
            def controllerSpanIndex = 0
            if (hasHandlerSpan()) {
              controllerSpanIndex++
            }
            if (hasForwardSpan()) {
              forwardSpan(it, spanIndex++, span(0), errorMessage, expectedExceptionClass())
              controllerSpanIndex++
            }
            if (hasIncludeSpan()) {
              includeSpan(it, spanIndex++, span(0), errorMessage, expectedExceptionClass())
              controllerSpanIndex++
            }
            controllerSpan(it, spanIndex++, span(controllerSpanIndex), errorMessage, expectedExceptionClass())
            if (hasRenderSpan(endpoint)) {
              renderSpan(it, spanIndex++, span(0), method, endpoint)
            }
            if (hasResponseSpan(endpoint)) {
              responseSpan(it, spanIndex, span(spanIndex - 1), span(0), method, endpoint)
              spanIndex++
            }
            if (hasErrorPageSpans(endpoint)) {
              errorPageSpans(it, spanIndex, span(0), method, endpoint)
            }
          } else if (hasResponseSpan(endpoint)) {
            responseSpan(it, 1, span(0), span(0), method, endpoint)
          }
        }
      }
    }
  }

  void controllerSpan(TraceAssert trace, int index, Object parent, String errorMessage = null, Class exceptionClass = Exception) {
    trace.span(index) {
      name "controller"
      errored errorMessage != null
      if (errorMessage) {
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

  void forwardSpan(TraceAssert trace, int index, Object parent, String errorMessage = null, Class exceptionClass = Exception) {
    trace.span(index) {
      name ~/\.forward$/
      kind SpanKind.INTERNAL
      errored errorMessage != null
      if (errorMessage) {
        errorEvent(exceptionClass, errorMessage)
      }
      childOf((SpanData) parent)
    }
  }

  void includeSpan(TraceAssert trace, int index, Object parent, String errorMessage = null, Class exceptionClass = Exception) {
    trace.span(index) {
      name ~/\.include$/
      kind SpanKind.INTERNAL
      errored errorMessage != null
      if (errorMessage) {
        errorEvent(exceptionClass, errorMessage)
      }
      childOf((SpanData) parent)
    }
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", Long responseContentLength = null, ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name expectedServerSpanName(endpoint)
      kind SpanKind.SERVER // can't use static import because of SERVER type parameter
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentSpanId parentID
      } else {
        hasNoParent()
      }
      if (endpoint == EXCEPTION && !hasHandlerSpan()) {
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
        "${SemanticAttributes.NET_PEER_PORT.key}" { it == null || it instanceof Long }
        "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.HTTP_CLIENT_IP.key}" { it == null || it == TEST_CLIENT_IP }
        "${SemanticAttributes.HTTP_URL.key}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "${SemanticAttributes.HTTP_METHOD.key}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key}" endpoint.status
        "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT
      }
    }
  }
}
