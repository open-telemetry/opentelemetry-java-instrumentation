/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package boot

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpData
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.common.QueryParams
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.web.util.OnCommittedResponseWrapper
import org.springframework.web.servlet.view.RedirectView

import java.util.regex.Pattern

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.LOGIN
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class AbstractSpringBootBasedTest extends HttpServerTest<ConfigurableApplicationContext> implements AgentTestTrait {

  abstract Class<?> securityConfigClass()

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(AppConfig, securityConfigClass())
    app.setDefaultProperties([
      "server.port"                 : port,
      "server.context-path"         : getContextPath(),
      "server.servlet.contextPath"  : getContextPath(),
      "server.error.include-message": "always",
    ])
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
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/**"
      case LOGIN:
        return getContextPath() + "/*"
      default:
        return super.expectedHttpRoute(endpoint, method)
    }
  }

  def "test spans with auth error"() {
    setup:
    def authProvider = server.getBean(SavingAuthenticationProvider)
    def request = request(AUTH_ERROR, "GET")

    when:
    authProvider.latestAuthentications.clear()
    def response = client.execute(request).aggregate().join()

    then:
    response.status().code() == 401 // not secured

    and:
    assertTraces(1) {
      trace(0, 3) {
        serverSpan(it, 0, null, null, "GET", AUTH_ERROR)
        sendErrorSpan(it, 1, span(0))
        errorPageSpans(it, 2, null)
      }
    }
  }

  def "test character encoding of #testPassword"() {
    setup:
    def authProvider = server.getBean(SavingAuthenticationProvider)

    QueryParams form = QueryParams.of("username", "test", "password", testPassword)
    def request = AggregatedHttpRequest.of(
      request(LOGIN, "POST").headers().toBuilder().contentType(MediaType.FORM_DATA).build(),
      HttpData.ofUtf8(form.toQueryString()))

    when:
    authProvider.latestAuthentications.clear()
    def response = client.execute(request).aggregate().join()

    then:
    response.status().code() == 302 // redirect after success
    authProvider.latestAuthentications.get(0).password == testPassword

    and:
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, null, null, "POST", LOGIN)
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
    def methodName = endpoint == NOT_FOUND ? "sendError" : "sendRedirect"
    // the response wrapper class names vary depending on spring version & scenario
    def namePattern = Pattern.compile("(OnCommittedResponseWrapper|HttpServletResponseWrapper|FirewalledResponse).$methodName")
    trace.span(index) {
      name namePattern
      kind INTERNAL
      attributes {
        "$SemanticAttributes.CODE_NAMESPACE" {
          it == OnCommittedResponseWrapper.name
            || it == "org.springframework.security.web.firewall.FirewalledResponse"
            || it == "jakarta.servlet.http.HttpServletResponseWrapper"
        }
        "$SemanticAttributes.CODE_FUNCTION" methodName
      }
    }
  }

  @Override
  void renderSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "Render RedirectView"
      kind INTERNAL
      attributes {
        "spring-webmvc.view.type" RedirectView.name
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
