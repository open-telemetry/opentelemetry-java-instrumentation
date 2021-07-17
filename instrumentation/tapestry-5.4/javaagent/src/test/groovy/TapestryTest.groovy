/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.testing.internal.armeria.client.ClientRequestContext
import io.opentelemetry.testing.internal.armeria.client.DecoratingHttpClientFunction
import io.opentelemetry.testing.internal.armeria.client.HttpClient
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames
import io.opentelemetry.testing.internal.armeria.common.HttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.jsoup.Jsoup

class TapestryTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  @Override
  Server startServer(int port) {
    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    // set up test application
    webAppContext.setBaseResource(Resource.newResource("src/test/webapp"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    return "/jetty-context"
  }

  WebClient client

  def setup() {
    client = WebClient.builder(address)
      .decorator(new DecoratingHttpClientFunction() {
        // https://github.com/line/armeria/issues/2489
        @Override
        HttpResponse execute(HttpClient delegate, ClientRequestContext ctx, HttpRequest req) throws Exception {
          return HttpResponse.from(delegate.execute(ctx, req).aggregate().thenApply {resp ->
            if (resp.status().isRedirection()) {
              return delegate.execute(ctx, HttpRequest.of(req.method(), URI.create(resp.headers().get(HttpHeaderNames.LOCATION)).path))
            }
            return resp.toHttpResponse()
          })
        }
      })
      .build()
  }

  static serverSpan(TraceAssert trace, int index, String spanName) {
    trace.span(index) {
      hasNoParent()

      name spanName
      kind SpanKind.SERVER
    }
  }

  def "test index page"() {
    setup:
    AggregatedHttpResponse response = client.get("/").aggregate().join()
    def doc = Jsoup.parse(response.contentUtf8())

    expect:
    response.status().code() == 200
    doc.selectFirst("title").text() == "Index page"

    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/Index")
        basicSpan(it, 1, "activate/Index", span(0))
      }
    }
  }

  def "test start action"() {
    setup:
    // index.start triggers an action named "start" on index page
    AggregatedHttpResponse response = client.get("/index.start").aggregate().join()
    def doc = Jsoup.parse(response.contentUtf8())

    expect:
    response.status().code() == 200
    doc.selectFirst("title").text() == "Other page"

    assertTraces(2) {
      trace(0, 4) {
        serverSpan(it, 0, getContextPath() + "/Index")
        basicSpan(it, 1, "activate/Index", span(0))
        basicSpan(it, 2, "action/Index:start", span(0))
        basicSpan(it, 3, "Response.sendRedirect", span(2))
      }
      trace(1, 2) {
        serverSpan(it, 0, getContextPath() + "/Other")
        basicSpan(it, 1, "activate/Other", span(0))
      }
    }
  }

  def "test exception action"() {
    setup:
    // index.exception triggers an action named "exception" on index page
    AggregatedHttpResponse response = client.get("/index.exception").aggregate().join()

    expect:
    response.status().code() == 500

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          hasNoParent()
          kind SpanKind.SERVER
          name getContextPath() + "/Index"
          status ERROR
        }
        basicSpan(it, 1, "activate/Index", span(0))
        basicSpan(it, 2, "action/Index:exception", span(0), new IllegalStateException("expected"))
      }
    }
  }
}
