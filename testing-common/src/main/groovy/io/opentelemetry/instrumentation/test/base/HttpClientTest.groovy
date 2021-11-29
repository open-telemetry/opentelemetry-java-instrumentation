/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import static org.junit.jupiter.api.Assumptions.assumeTrue

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.trace.data.SpanData
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

@Unroll
abstract class HttpClientTest<REQUEST> extends InstrumentationSpecification {
  protected static final BODY_METHODS = ["POST", "PUT"]
  protected static final CONNECT_TIMEOUT_MS = 5000
  protected static final READ_TIMEOUT_MS = 2000

  /**
   * Build the request to be passed to
   * {@link #sendRequest(java.lang.Object, java.lang.String, java.net.URI, java.util.Map)}.
   *
   * By splitting this step out separate from {@code sendRequest}, tests and re-execute the same
   * request a second time to verify that the traceparent header is not added multiple times to the
   * request, and that the last one wins. Tests will fail if the header shows multiple times.
   */
  abstract REQUEST buildRequest(String method, URI uri, Map<String, String> headers)

  /**
   * Make the request and return the status code of the response synchronously. Some clients, e.g.,
   * HTTPUrlConnection only support synchronous execution without callbacks, and many offer a
   * dedicated API for invoking synchronously, such as OkHttp's execute method. When implementing
   * this method, such an API should be used and the HTTP status code of the response returned,
   * for example:
   *
   * <pre>
   * @Override
   * int sendRequest(Request request, String method, URI uri, Map<String, String headers = [:]) {
   *   HttpResponse response = client.execute(request)
   *   return response.statusCode()
   * }
   * </pre>
   *
   * If there is no synchronous API available at all, for example as in Vert.X, a CompletableFuture
   * can be used to block on a result, for example:
   *
   * <pre>
   * @Override
   * int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
   *   CompletableFuture<Integer> future = new CompletableFuture<>(
   *   sendRequestWithCallback(request, method, uri, headers) {
   *     future.complete(it.statusCode())
   *   }
   *   return future.get()
   * }
   * </pre>
   */
  abstract int sendRequest(REQUEST request, String method, URI uri, Map<String, String> headers)

  /**
   * Make the request and return the status code of the response through the callback. This method
   * should be implemented if the client offers any request execution methods that accept a callback
   * which receives the response. This will generally be an API for asynchronous execution of a
   * request, such as OkHttp's enqueue method, but may also be a callback executed synchronously,
   * such as ApacheHttpClient's response handler callbacks. This method is used in tests to verify
   * the context is propagated correctly to such callbacks.
   *
   * <pre>
   * @Override
   * void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {
   *   // Hypothetical client accepting a callback
   *   client.executeAsync(request) {
   *     void success(Response response) {
   *       requestResult.complete(response.statusCode())
   *     }
   *     void failure(Throwable throwable) {
   *       requestResult.complete(throwable)
   *     }
   *   }
   *
   *   // Hypothetical client returning a CompletableFuture
   *   client.executeAsync(request).whenComplete { response, throwable ->
   *     requestResult.complete({ response.statusCode() }, throwable)
   *   }
   * }
   * </pre>
   *
   * If the client offers no APIs that accept callbacks, then this method should not be implemented
   * and instead, {@link #testCallback} should be implemented to return false.
   */
  void sendRequestWithCallback(REQUEST request, String method, URI uri, Map<String, String> headers,
                               AbstractHttpClientTest.RequestResult requestResult) {
    // Must be implemented if testAsync is true
    throw new UnsupportedOperationException()
  }

  @Shared
  def junitTest = new AbstractHttpClientTest() {
    @Override
    protected buildRequest(String method, URI uri, Map<String, String> headers) {
      return HttpClientTest.this.buildRequest(method, uri, headers)
    }

    @Override
    protected int sendRequest(def request, String method, URI uri, Map<String, String> headers) {
      return HttpClientTest.this.sendRequest(request, method, uri, headers)
    }

    @Override
    protected void sendRequestWithCallback(def request, String method, URI uri, Map<String, String> headers,
                                           AbstractHttpClientTest.RequestResult requestResult) {
      HttpClientTest.this.sendRequestWithCallback(request, method, uri, headers, requestResult)
    }

    @Override
    protected String expectedClientSpanName(URI uri, String method) {
      return HttpClientTest.this.expectedClientSpanName(uri, method)
    }

    @Override
    protected Integer responseCodeOnRedirectError() {
      return HttpClientTest.this.responseCodeOnRedirectError()
    }

    @Override
    protected String userAgent() {
      return HttpClientTest.this.userAgent()
    }

    @Override
    protected Throwable clientSpanError(URI uri, Throwable exception) {
      return HttpClientTest.this.clientSpanError(uri, exception)
    }

    @Override
    protected Set<AttributeKey<?>> httpAttributes(URI uri) {
      return HttpClientTest.this.httpAttributes(uri)
    }

    @Override
    protected SingleConnection createSingleConnection(String host, int port) {
      return HttpClientTest.this.createSingleConnection(host, port)
    }

    @Override
    protected boolean testWithClientParent() {
      return HttpClientTest.this.testWithClientParent()
    }

    @Override
    protected boolean testRedirects() {
      return HttpClientTest.this.testRedirects()
    }

    @Override
    protected boolean testCircularRedirects() {
      return HttpClientTest.this.testCircularRedirects()
    }

    // maximum number of redirects that http client follows before giving up
    @Override
    protected int maxRedirects() {
      return HttpClientTest.this.maxRedirects()
    }

    @Override
    protected boolean testReusedRequest() {
      return HttpClientTest.this.testReusedRequest()
    }

    @Override
    protected boolean testConnectionFailure() {
      return HttpClientTest.this.testConnectionFailure()
    }

    @Override
    protected boolean testRemoteConnection() {
      return HttpClientTest.this.testRemoteConnection()
    }

    @Override
    protected boolean testReadTimeout() {
      return HttpClientTest.this.testReadTimeout()
    }

    @Override
    protected boolean testHttps() {
      return HttpClientTest.this.testHttps()
    }

    @Override
    protected boolean testCausality() {
      return HttpClientTest.this.testCausality()
    }

    @Override
    protected boolean testCausalityWithCallback() {
      return HttpClientTest.this.testCausalityWithCallback()
    }

    @Override
    protected boolean testCallback() {
      return HttpClientTest.this.testCallback()
    }

    @Override
    protected boolean testCallbackWithParent() {
      // FIXME: this hack is here because callback with parent is broken in play-ws when the stream()
      // function is used.  There is no way to stop a test from a derived class hence the flag
      return HttpClientTest.this.testCallbackWithParent()
    }

    @Override
    protected boolean testErrorWithCallback() {
      return HttpClientTest.this.testErrorWithCallback()
    }

    @Override
    protected String nonRoutableAddress() {
      return HttpClientTest.this.nonRoutableAddress()
    }
  }

  @Shared
  HttpClientTestServer server

  def setupSpec() {
    server = new HttpClientTestServer(openTelemetry)
    server.start()
    junitTest.setupOptions()
    junitTest.setTesting(testRunner(), server)
  }

  def cleanupSpec() {
    server.stop()
  }

  static int getPort(URI uri) {
    if (uri.port != -1) {
      return uri.port
    } else if (uri.scheme == "http") {
      return 80
    } else if (uri.scheme == "https") {
      443
    } else {
      throw new IllegalArgumentException("Unexpected uri: $uri")
    }
  }

  def "basic GET request #path"() {
    expect:
    junitTest.successfulGetRequest(path)

    where:
    path << ["/success", "/success?with=params"]
  }

  def "basic #method request with parent"() {
    expect:
    junitTest.successfulRequestWithParent(method)

    where:
    method << BODY_METHODS
  }

  def "basic GET request with not sampled parent"() {
    expect:
    junitTest.successfulRequestWithNotSampledParent()
  }

  def "should suppress nested CLIENT span if already under parent CLIENT span (#method)"() {
    assumeTrue(testWithClientParent())
    expect:
    junitTest.shouldSuppressNestedClientSpanIfAlreadyUnderParentClientSpan(method)

    where:
    method << BODY_METHODS
  }

  //FIXME: add tests for POST with large/chunked data

  def "trace request with callback and parent"() {
    assumeTrue(testCallback())
    assumeTrue(testCallbackWithParent())
    expect:
    junitTest.requestWithCallbackAndParent()
  }

  def "trace request with callback and no parent"() {
    assumeTrue(testCallback())
    expect:
    junitTest.requestWithCallbackAndNoParent()
  }

  def "basic request with 1 redirect"() {
    assumeTrue(testRedirects())
    expect:
    junitTest.basicRequestWith1Redirect()
  }

  def "basic request with 2 redirects"() {
    assumeTrue(testRedirects())
    expect:
    junitTest.basicRequestWith2Redirects()
  }

  def "basic request with circular redirects"() {
    assumeTrue(testRedirects())
    assumeTrue(testCircularRedirects())
    expect:
    junitTest.circularRedirects()
  }

  def "redirect to secured endpoint copies auth header"() {
    assumeTrue(testRedirects())
    expect:
    junitTest.redirectToSecuredCopiesAuthHeader()
  }

  def "error span"() {
    expect:
    junitTest.errorSpan()
  }

  def "reuse request"() {
    assumeTrue(testReusedRequest())
    expect:
    junitTest.reuseRequest()
  }

  // this test verifies two things:
  // * the javaagent doesn't cause multiples of tracing headers to be added
  //   (TestHttpServer throws exception if there are multiples)
  // * the javaagent overwrites the existing tracing headers
  //   (so that it propagates the same trace id / span id that it reports to the backend
  //   and the trace is not broken)
  def "request with existing tracing headers"() {
    expect:
    junitTest.requestWithExistingTracingHeaders()
  }

  def "connection error (unopened port)"() {
    assumeTrue(testConnectionFailure())
    expect:
    junitTest.connectionErrorUnopenedPort()
  }

  def "connection error (unopened port) with callback"() {
    assumeTrue(testConnectionFailure())
    assumeTrue(testCallback())
    assumeTrue(testErrorWithCallback())
    expect:
    junitTest.connectionErrorUnopenedPortWithCallback()
  }

  def "connection error non routable address"() {
    assumeTrue(testRemoteConnection())
    expect:
    junitTest.connectionErrorNonRoutableAddress()
  }

  def "read timed out"() {
    assumeTrue(testReadTimeout())
    expect:
    junitTest.readTimedOut()
  }

  // IBM JVM has different protocol support for TLS
  @Requires({ !System.getProperty("java.vm.name").contains("IBM J9 VM") })
  def "test https request"() {
    assumeTrue(testRemoteConnection())
    assumeTrue(testHttps())
    expect:
    junitTest.httpsRequest()
  }

  /**
   * This test fires a large number of concurrent requests.
   * Each request first hits a HTTP server and then makes another client request.
   * The goal of this test is to verify that in highly concurrent environment our instrumentations
   * for http clients (especially inherently concurrent ones, such as Netty or Reactor) correctly
   * propagate trace context.
   */
  def "high concurrency test"() {
    assumeTrue(testCausality())
    expect:
    junitTest.highConcurrency()
  }

  def "high concurrency test with callback"() {
    assumeTrue(testCausality())
    assumeTrue(testCausalityWithCallback())
    assumeTrue(testCallback())
    assumeTrue(testCallbackWithParent())
    expect:
    junitTest.highConcurrencyWithCallback()
  }

  /**
   * Almost similar to the "high concurrency test" test above, but all requests use the same single
   * connection.
   */
  def "high concurrency test on single connection"() {
    SingleConnection singleConnection = createSingleConnection("localhost", server.httpPort())
    assumeTrue(singleConnection != null)
    expect:
    junitTest.highConcurrencyOnSingleConnection()
  }

  // ideally private, but then groovy closures in this class cannot find them
  final int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    def request = buildRequest(method, uri, headers)
    return sendRequest(request, method, uri, headers)
  }

  protected String expectedClientSpanName(URI uri, String method) {
    return method != null ? "HTTP " + method : "HTTP request"
  }

  Integer responseCodeOnRedirectError() {
    return null
  }

  String userAgent() {
    return null
  }

  /** A list of additional HTTP client span attributes extracted by the instrumentation per URI. */
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES)
  }

  //This method should create either a single connection to the target uri or a http client
  //which is guaranteed to use the same connection for all requests
  SingleConnection createSingleConnection(String host, int port) {
    return null
  }

  boolean testWithClientParent() {
    true
  }

  boolean testRedirects() {
    true
  }

  boolean testCircularRedirects() {
    true
  }

  // maximum number of redirects that http client follows before giving up
  int maxRedirects() {
    2
  }

  boolean testReusedRequest() {
    true
  }

  boolean testConnectionFailure() {
    true
  }

  boolean testRemoteConnection() {
    true
  }

  boolean testReadTimeout() {
    false
  }

  boolean testHttps() {
    true
  }

  boolean testCausality() {
    true
  }

  boolean testCausalityWithCallback() {
    true
  }

  boolean testCallback() {
    return true
  }

  boolean testCallbackWithParent() {
    // FIXME: this hack is here because callback with parent is broken in play-ws when the stream()
    // function is used.  There is no way to stop a test from a derived class hence the flag
    true
  }

  boolean testErrorWithCallback() {
    return true
  }

  String nonRoutableAddress() {
    HttpClientTestOptions.DEFAULT_NON_ROUTABLE_ADDRESS
  }

  Throwable clientSpanError(URI uri, Throwable exception) {
    return exception
  }

  final void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", URI uri = resolveAddress("/success"), Integer responseCode = 200) {
    trace.assertedIndexes.add(index)
    def spanData = trace.span(index)
    def assertion = junitTest.assertClientSpan(OpenTelemetryAssertions.assertThat(spanData), uri, method, responseCode)
    if (parentSpan == null) {
      assertion.hasParentSpanId(SpanId.invalid)
    } else {
      assertion.hasParentSpanId(((SpanData) parentSpan).spanId)
    }
  }

  final void serverSpan(TraceAssert trace, int index, Object parentSpan = null) {
    trace.assertedIndexes.add(index)
    def spanData = trace.span(index)
    def assertion = junitTest.assertServerSpan(OpenTelemetryAssertions.assertThat(spanData))
    if (parentSpan == null) {
      assertion.hasParentSpanId(SpanId.invalid)
    } else {
      assertion.hasParentSpanId(((SpanData) parentSpan).spanId)
    }
  }

  final URI resolveAddress(String path) {
    return junitTest.resolveAddress(path)
  }
}
