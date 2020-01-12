package io.opentelemetry.auto.test.base

import io.opentelemetry.auto.api.Config
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.decorator.HttpClientDecorator
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.ExecutionException

import static io.opentelemetry.auto.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

@Unroll
abstract class HttpClientTest<DECORATOR extends HttpClientDecorator> extends AgentTestRunner {
  protected static final BODY_METHODS = ["POST", "PUT"]

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        handleDistributedRequest()
        String msg = "Hello."
        response.status(200).send(msg)
      }
      prefix("error") {
        handleDistributedRequest()
        String msg = "Sorry."
        response.status(500).send(msg)
      }
      prefix("redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/success").toURL().toString())
      }
      prefix("another-redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/redirect").toURL().toString())
      }
      prefix("circular-redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/circular-redirect").toURL().toString())
      }
    }
  }

  @Shared
  DECORATOR clientDecorator = decorator()

  /**
   * Make the request and return the status code response
   * @param method
   * @return
   */
  abstract int doRequest(String method, URI uri, Map<String, String> headers = [:], Closure callback = null)

  abstract DECORATOR decorator()

  Integer statusOnRedirectError() {
    return null
  }

  def "basic #method request #url - tagQueryString=#tagQueryString"() {
    when:
    def status = withConfigOverride(Config.HTTP_CLIENT_TAG_QUERY_STRING, "$tagQueryString") {
      doRequest(method, url)
    }

    then:
    status == 200
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        clientSpan(it, 0, null, method, false, tagQueryString, url)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    path                                | tagQueryString
    "/success"                          | false
    "/success"                          | true
    "/success?with=params"              | false
    "/success?with=params"              | true
    "/success#with+fragment"            | true
    "/success?with=params#and=fragment" | true

    method = "GET"
    url = server.address.resolve(path)
  }

  def "basic #method request with parent"() {
    when:
    def status = runUnderTrace("parent") {
      doRequest(method, server.address.resolve("/success"))
    }

    then:
    status == 200
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0), method, false)
        serverSpan(it, 2 + extraClientSpans(), span(1 + extraClientSpans()))
      }
    }

    where:
    method << BODY_METHODS
  }

  //FIXME: add tests for POST with large/chunked data

  def "basic #method request with split-by-domain"() {
    when:
    def status = withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true") {
      doRequest(method, server.address.resolve("/success"))
    }

    then:
    status == 200
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        clientSpan(it, 0, null, method, true)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "HEAD"
  }

  def "trace request without propagation"() {
    when:
    def status = withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("parent") {
        doRequest(method, server.address.resolve("/success"), ["is-test-server": "false"])
      }
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0), method, renameService)
      }
    }

    where:
    method = "GET"
    renameService << [false, true]
  }

  def "trace request with callback and parent"() {
    when:
    def status = runUnderTrace("parent") {
      doRequest(method, server.address.resolve("/success"), ["is-test-server": "false"]) {
        runUnderTrace("child") {}
      }
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0), method, false)
        basicSpan(it, 2 + extraClientSpans(), "child", null, span(0))
      }
    }

    where:
    method = "GET"
  }

  def "trace request with callback and no parent"() {
    when:
    def status = doRequest(method, server.address.resolve("/success"), ["is-test-server": "false"]) {
      runUnderTrace("callback") {
      }
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(2) {
      trace(0, 1 + extraClientSpans()) {
        clientSpan(it, 0, null, method, false)
      }
      trace(1, 1) {
        basicSpan(it, 0, "callback")
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with 1 redirect"() {
    // TODO quite a few clients create an extra span for the redirect
    // This test should handle both types or we should unify how the clients work

    given:
    assumeTrue(testRedirects())
    def uri = server.address.resolve("/redirect")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        clientSpan(it, 0, null, method, false, false, uri)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 2 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with 2 redirects"() {
    given:
    assumeTrue(testRedirects())
    def uri = server.address.resolve("/another-redirect")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 4 + extraClientSpans()) {
        clientSpan(it, 0, null, method, false, false, uri)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 2 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 3 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with circular redirects"() {
    given:
    assumeTrue(testRedirects())
    assumeTrue(testCircularRedirects())
    def uri = server.address.resolve("/circular-redirect")

    when:
    doRequest(method, uri)//, ["is-test-server": "false"])

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        clientSpan(it, 0, null, method, false, false, uri, statusOnRedirectError(), thrownException)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 2 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  def "connection error (unopened port)"() {
    given:
    assumeTrue(testConnectionFailure())
    def uri = new URI("http://localhost:$UNUSABLE_PORT/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        basicSpan(it, 0, "parent", null, null, thrownException)
        clientSpan(it, 1, span(0), method, false, false, uri, null, thrownException)
      }
    }

    where:
    method = "GET"
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", boolean renameService = false, boolean tagQueryString = false, URI uri = server.address.resolve("/success"), Integer status = 200, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      operationName expectedOperationName()
      errored exception != null
      tags {
        "$MoreTags.SERVICE_NAME" renameService ? "localhost" : null
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
        "$Tags.COMPONENT" clientDecorator.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
        "$Tags.PEER_HOSTNAME" "localhost"
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.PEER_PORT" uri.port
        "$Tags.HTTP_URL" "${uri.resolve(uri.path)}"
        "$Tags.HTTP_METHOD" method
        if (status) {
          "$Tags.HTTP_STATUS" status
        }
        if (tagQueryString) {
          "$MoreTags.HTTP_QUERY" uri.query
          "$MoreTags.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
        }
        if (exception) {
          errorTags(exception.class, exception.message)
        }
      }
    }
  }

  void serverSpan(TraceAssert traces, int index, Object parentSpan = null) {
    traces.span(index) {
      operationName "test-http-server"
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      tags {
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
      }
    }
  }

  String expectedOperationName() {
    return "http.request"
  }

  int extraClientSpans() {
    0
  }

  boolean testRedirects() {
    true
  }

  boolean testCircularRedirects() {
    true
  }

  boolean testConnectionFailure() {
    true
  }
}
