/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.ClientAttributes
import io.opentelemetry.semconv.UserAgentAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.NetworkAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpData
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.common.QueryParams
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.jsoup.Jsoup
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.StatusCode.ERROR

abstract class BaseJsfTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  def setupSpec() {
    setupServer()
  }

  def cleanupSpec() {
    cleanupServer()
  }

  @Override
  Server startServer(int port) {
    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app"))

    Resource extraResource = Resource.newSystemResource("test-app-extra")
    if (extraResource != null) {
      webAppContext.getMetaData().addWebInfResource(extraResource)
    }

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

  @Unroll
  def "test #path"() {
    setup:
    AggregatedHttpResponse response = client.get(address.resolve(path).toString()).aggregate().join()

    expect:
    response.status().code() == 200
    response.contentUtf8().trim() == "Hello"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name getContextPath() + "/hello.xhtml"
          kind SpanKind.SERVER
          hasNoParent()
          attributes {
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_ADDRESS" "localhost"
            "$ServerAttributes.SERVER_PORT" port
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" "127.0.0.1"
            "$NetworkAttributes.NETWORK_PEER_PORT" Long
            "$HttpAttributes.HTTP_REQUEST_METHOD" "GET"
            "$UrlAttributes.URL_SCHEME" "http"
            "$UrlAttributes.URL_PATH" "/jetty-context/" + path
            "$UserAgentAttributes.USER_AGENT_ORIGINAL" TEST_USER_AGENT
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$HttpAttributes.HTTP_ROUTE" "/jetty-context/" + route
            "$ClientAttributes.CLIENT_ADDRESS" { it == null || it == TEST_CLIENT_IP }
          }
        }
      }
    }

    where:
    path                | route
    "hello.xhtml"       | "*.xhtml"
    "faces/hello.xhtml" | "faces/*"
  }

  def "test greeting"() {
    // we need to display the page first before posting data to it
    setup:
    AggregatedHttpResponse response = client.get(address.resolve("greeting.xhtml").toString()).aggregate().join()
    def doc = Jsoup.parse(response.contentUtf8())

    expect:
    response.status().code() == 200
    doc.selectFirst("title").text() == "Hello, World!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name getContextPath() + "/greeting.xhtml"
          kind SpanKind.SERVER
          hasNoParent()
        }
      }
    }
    clearExportedData()

    when:
    // extract parameters needed to post back form
    def viewState = doc.selectFirst("[name=jakarta.faces.ViewState]")?.val()
    def formAction = doc.selectFirst("#app-form").attr("action")
    def jsessionid = formAction.substring(formAction.indexOf("jsessionid=") + "jsessionid=".length())

    then:
    viewState != null
    jsessionid != null

    when:
    // set up form parameter for post
    QueryParams formBody = QueryParams.builder()
      .add("app-form", "app-form")
    // value used for name is returned in app-form:output-message element
      .add("app-form:name", "test")
      .add("app-form:submit", "Say hello")
      .add("app-form_SUBMIT", "1") // MyFaces
      .add("jakarta.faces.ViewState", viewState)
      .build()
    // use the session created for first request
    def request2 = AggregatedHttpRequest.of(
      RequestHeaders.builder(HttpMethod.POST, address.resolve("greeting.xhtml;jsessionid=" + jsessionid).toString())
        .contentType(MediaType.FORM_DATA)
        .build(),
      HttpData.ofUtf8(formBody.toQueryString()))
    AggregatedHttpResponse response2 = client.execute(request2).aggregate().join()
    def responseContent = response2.contentUtf8()
    def doc2 = Jsoup.parse(responseContent)

    then:
    response2.status().code() == 200
    doc2.getElementById("app-form:output-message").text() == "Hello test"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name getContextPath() + "/greeting.xhtml"
          kind SpanKind.SERVER
          hasNoParent()
        }
        handlerSpan(it, 1, span(0), "#{greetingForm.submit()}")
      }
    }
  }

  def "test exception"() {
    // we need to display the page first before posting data to it
    setup:
    AggregatedHttpResponse response = client.get(address.resolve("greeting.xhtml").toString()).aggregate().join()
    def doc = Jsoup.parse(response.contentUtf8())

    expect:
    response.status().code() == 200
    doc.selectFirst("title").text() == "Hello, World!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name getContextPath() + "/greeting.xhtml"
          kind SpanKind.SERVER
          hasNoParent()
        }
      }
    }
    clearExportedData()

    when:
    // extract parameters needed to post back form
    def viewState = doc.selectFirst("[name=jakarta.faces.ViewState]").val()
    def formAction = doc.selectFirst("#app-form").attr("action")
    def jsessionid = formAction.substring(formAction.indexOf("jsessionid=") + "jsessionid=".length())

    then:
    viewState != null
    jsessionid != null

    when:
    // set up form parameter for post
    QueryParams formBody = QueryParams.builder()
      .add("app-form", "app-form")
    // setting name parameter to "exception" triggers throwing exception in GreetingForm
      .add("app-form:name", "exception")
      .add("app-form:submit", "Say hello")
      .add("app-form_SUBMIT", "1") // MyFaces
      .add("jakarta.faces.ViewState", viewState)
      .build()
    // use the session created for first request
    def request2 = AggregatedHttpRequest.of(
      RequestHeaders.builder(HttpMethod.POST, address.resolve("greeting.xhtml;jsessionid=" + jsessionid).toString())
        .contentType(MediaType.FORM_DATA)
        .build(),
      HttpData.ofUtf8(formBody.toQueryString()))
    AggregatedHttpResponse response2 = client.execute(request2).aggregate().join()

    then:
    response2.status().code() == 500
    def ex = new Exception("submit exception")

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name getContextPath() + "/greeting.xhtml"
          kind SpanKind.SERVER
          hasNoParent()
          status ERROR
          errorEvent(ex.class, ex.message)
        }
        handlerSpan(it, 1, span(0), "#{greetingForm.submit()}", ex)
      }
    }
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, String spanName, Exception expectedException = null) {
    trace.span(index) {
      name spanName
      kind INTERNAL
      if (expectedException != null) {
        status ERROR
        errorEvent(expectedException.getClass(), expectedException.getMessage())
      }
      childOf((SpanData) parent)
    }
  }
}
