/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.LOGIN
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import okhttp3.FormBody
import okhttp3.RequestBody
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.servlet.view.RedirectView

class SpringBootBasedTest extends HttpServerTest<ConfigurableApplicationContext> implements AgentTestTrait {

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(AppConfig, SecurityConfig, AuthServerConfig)
    app.setDefaultProperties([
      "server.port"                 : port,
      "server.context-path"         : getContextPath(),
      "server.servlet.contextPath"  : getContextPath(),
      "server.error.include-message": "always"])
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  String getContextPath() {
    return "/xyz"
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    true
  }

  @Override
  boolean hasRenderSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == NOT_FOUND
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    endpoint == NOT_FOUND
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/**"
      case LOGIN:
        return getContextPath() + "/*"
      default:
        return super.expectedServerSpanName(endpoint)
    }
  }

  def "test spans with auth error"() {
    setup:
    def authProvider = server.getBean(SavingAuthenticationProvider)
    def request = request(AUTH_ERROR, "GET", null).build()

    when:
    authProvider.latestAuthentications.clear()
    def response = client.newCall(request).execute()

    then:
    response.code() == 401 // not secured

    and:
    assertTraces(1) {
      trace(0, 3) {
        serverSpan(it, 0, null, null, "GET", null, AUTH_ERROR)
        sendErrorSpan(it, 1, span(0))
        errorPageSpans(it, 2, null)
      }
    }
  }

  def "test character encoding of #testPassword"() {
    setup:
    def authProvider = server.getBean(SavingAuthenticationProvider)

    RequestBody formBody = new FormBody.Builder()
      .add("username", "test")
      .add("password", testPassword).build()

    def request = request(LOGIN, "POST", formBody).build()

    when:
    authProvider.latestAuthentications.clear()
    def response = client.newCall(request).execute()

    then:
    response.code() == 302 // redirect after success
    authProvider.latestAuthentications.get(0).password == testPassword

    and:
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, null, null, "POST", response.body()?.contentLength(), LOGIN)
        redirectSpan(it, 1, span(0))
      }
    }

    where:
    testPassword << ["password", "dfsdföääöüüä", "🤓"]
  }

  @Override
  void errorPageSpans(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "BasicErrorController.error"
      kind INTERNAL
      attributes {
      }
    }
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    def responseSpanName = endpoint == NOT_FOUND ? "OnCommittedResponseWrapper.sendError" : "OnCommittedResponseWrapper.sendRedirect"
    trace.span(index) {
      name responseSpanName
      kind INTERNAL
      attributes {
      }
    }
  }

  @Override
  void renderSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "Render RedirectView"
      kind INTERNAL
      attributes {
        "spring-webmvc.view.type" RedirectView.simpleName
      }
    }
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    def handlerSpanName = "TestController.${endpoint.name().toLowerCase()}"
    if (endpoint == NOT_FOUND) {
      handlerSpanName = "ResourceHttpRequestHandler.handleRequest"
    }
    trace.span(index) {
      name handlerSpanName
      kind INTERNAL
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(Exception, EXCEPTION.body)
      }
      childOf((SpanData) parent)
    }
  }
}
