package datadog.trace.agent.test.base

import datadog.opentracing.DDSpan
import datadog.trace.agent.decorator.HttpClientDecorator
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.ExecutionException

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.agent.test.utils.TraceUtils.withConfigOverride

abstract class HttpClientTest<T extends HttpClientDecorator> extends AgentTestRunner {

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
  T decorator = decorator()

  /**
   * Make the request and return the status code response
   * @param method
   * @return
   */
  abstract int doRequest(String method, URI uri, Map<String, String> headers = [:], Closure callback = null)

  abstract T decorator()

  Integer statusOnRedirectError() {
    return null
  }

  def "basic #method request"() {
    when:
    def status = doRequest(method, server.address.resolve("/success"))

    then:
    status == 200
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, trace(1).get(0))
      trace(1, 1) {
        clientSpan(it, 0, null, false)
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with parent"() {
    when:
    def status = runUnderTrace("parent") {
      doRequest(method, server.address.resolve("/success"))
    }

    then:
    status == 200
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, trace(1).get(1))
      trace(1, 2) {
        parentSpan(it, 0)
        clientSpan(it, 1, span(0), false)
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with split-by-domain"() {
    when:
    def status = withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "true") {
      doRequest(method, server.address.resolve("/success"))
    }

    then:
    status == 200
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, trace(1).get(0))
      trace(1, 1) {
        clientSpan(it, 0, null, true)
      }
    }

    where:
    method = "GET"
  }

  def "trace request without propagation"() {
    when:
    def status = withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("parent") {
        doRequest(method, server.address.resolve("/success"), ["is-dd-server": "false"])
      }
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(1) {
      trace(0, 2) {
        parentSpan(it, 0)
        clientSpan(it, 1, span(0), renameService)
      }
    }

    where:
    method = "GET"
    renameService << [false, true]
  }

  def "trace request with callback and parent"() {
    when:
    def status = runUnderTrace("parent") {
      doRequest(method, server.address.resolve("/success"), ["is-dd-server": "false"]) {
        runUnderTrace("child") {}
      }
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(1) {
      trace(0, 3) {
        parentSpan(it, 0)
        span(1) {
          operationName "child"
          childOf span(0)
        }
        clientSpan(it, 2, span(0), false)
      }
    }

    where:
    method = "GET"
  }

  def "trace request with callback and no parent"() {
    when:
    def status = doRequest(method, server.address.resolve("/success"), ["is-dd-server": "false"]) {
      runUnderTrace("child") {
        // Ensure consistent ordering of traces for assertion.
        TEST_WRITER.waitForTraces(1)
      }
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(2) {
      trace(0, 1) {
        clientSpan(it, 0, null, false)
      }
      trace(1, 1) {
        span(0) {
          operationName "child"
          parent()
        }
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with 1 redirect"() {
    setup:
    def uri = server.address.resolve("/redirect")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(3) {
      server.distributedRequestTrace(it, 0, trace(2).get(0))
      server.distributedRequestTrace(it, 1, trace(2).get(0))
      trace(2, 1) {
        clientSpan(it, 0, null, false, uri)
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with 2 redirects"() {
    setup:
    def uri = server.address.resolve("/another-redirect")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(4) {
      server.distributedRequestTrace(it, 0, trace(3).get(0))
      server.distributedRequestTrace(it, 1, trace(3).get(0))
      server.distributedRequestTrace(it, 2, trace(3).get(0))
      trace(3, 1) {
        clientSpan(it, 0, null, false, uri)
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with circular redirects"() {
    setup:
    def uri = server.address.resolve("/circular-redirect")

    when:
    doRequest(method, uri)//, ["is-dd-server": "false"])

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(3) {
      server.distributedRequestTrace(it, 0, trace(2).get(0))
      server.distributedRequestTrace(it, 1, trace(2).get(0))
      trace(2, 1) {
        clientSpan(it, 0, null, false, uri, statusOnRedirectError(), thrownException)
      }
    }

    where:
    method = "GET"
  }

  void parentSpan(TraceAssert trace, int index, Throwable exception = null) {
    trace.span(index) {
      parent()
      serviceName "unnamed-java-app"
      operationName "parent"
      resourceName "parent"
      errored exception != null
      tags {
        defaultTags()
        if (exception) {
          errorTags(exception.class)
        }
      }
    }
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void clientSpan(TraceAssert trace, int index, Object parentSpan, boolean renameService, URI uri = server.address.resolve("/success"), Integer status = 200, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf((DDSpan) parentSpan)
      }
      serviceName renameService ? "localhost" : "unnamed-java-app"
      operationName "http.request"
      resourceName "GET $uri.path"
      spanType DDSpanTypes.HTTP_CLIENT
      errored exception != null
      tags {
        defaultTags()
        if (exception) {
          errorTags(exception.class, exception.message)
        }
        "$Tags.COMPONENT.key" decorator.component()
        if (status) {
          "$Tags.HTTP_STATUS.key" status
        }
        "$Tags.HTTP_URL.key" "$uri"
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_PORT.key" server.address.port
        "$Tags.HTTP_METHOD.key" "GET"
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
      }
    }
  }
}
