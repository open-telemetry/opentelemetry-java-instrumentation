/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.LOGIN
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
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
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean hasRenderSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT
  }

  @Override
  boolean testNotFound() {
    // FIXME: the instrumentation adds an extra controller span which is not consistent.
    // Fix tests or remove extra span.
    false
  }

  @Override
  boolean testPathParam() {
    true
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
      trace(0, 4) {
        serverSpan(it, 0, null, null, "GET", null, AUTH_ERROR)
        sendErrorSpan(it, 1, span(0))
        forwardSpan(it, 2, span(0))
        errorPageSpans(it, 3, null)
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
      errored false
      attributes {
      }
    }
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "OnCommittedResponseWrapper.sendRedirect"
      kind INTERNAL
      errored false
      attributes {
      }
    }
  }

  @Override
  void renderSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "Render RedirectView"
      kind INTERNAL
      errored false
      attributes {
        "spring-webmvc.view.type" RedirectView.simpleName
      }
    }
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "TestController.${endpoint.name().toLowerCase()}"
      kind INTERNAL
      errored endpoint == EXCEPTION
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      childOf((SpanData) parent)
    }
  }

  // this override is needed because the exception is propagated up from the handler span
  // to the server span, which is different from the the expectation of the super method
  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", Long responseContentLength = null, ServerEndpoint endpoint = SUCCESS) {

    trace.span(index) {
      if (endpoint == PATH_PARAM) {
        name getContextPath() + "/path/{id}/param"
      } else if (endpoint == AUTH_ERROR) {
        name getContextPath() + "/error"
      } else if (endpoint == LOGIN) {
        name 'HTTP POST'
      } else {
        name endpoint.resolvePath(address).path
      }
      kind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentSpanId parentID
      } else {
        hasNoParent()
      }
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
        "${SemanticAttributes.NET_PEER_PORT.key}" Long
        "${SemanticAttributes.HTTP_URL.key}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "${SemanticAttributes.HTTP_METHOD.key}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key}" endpoint.status
        "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT
        "${SemanticAttributes.HTTP_CLIENT_IP.key}" TEST_CLIENT_IP
      }
    }
  }
}
