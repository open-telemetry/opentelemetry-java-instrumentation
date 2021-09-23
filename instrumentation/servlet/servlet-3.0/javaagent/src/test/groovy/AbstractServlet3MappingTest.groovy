/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import spock.lang.Unroll

import javax.servlet.Servlet
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.api.trace.StatusCode.ERROR

abstract class AbstractServlet3MappingTest<SERVER, CONTEXT> extends AgentInstrumentationSpecification implements HttpServerTestTrait<SERVER> {

  abstract void addServlet(CONTEXT context, String path, Class<Servlet> servlet)

  protected void setupServlets(CONTEXT context) {
    addServlet(context, "/prefix/*", TestServlet)
    addServlet(context, "*.suffix", TestServlet)
  }

  static class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      response.getWriter().write("Ok")
    }
  }

  @Unroll
  def "test path #path"() {
    setup:
    AggregatedHttpResponse response = client.get(address.resolve(path).toString()).aggregate().join()

    expect:
    response.status().code() == success ? 200 : 404

    and:
    def spanCount = success ? 1 : 2
    assertTraces(1) {
      trace(0, spanCount) {
        span(0) {
          name getContextPath() + spanName
          kind SpanKind.SERVER
          if (!success) {
            status ERROR
          }
        }
        if (!success) {
          span(1) {
          }
        }
      }
    }

    where:
    path       | spanName    | success
    'prefix'   | '/prefix/*' | true
    'prefix/'  | '/prefix/*' | true
    'prefix/a' | '/prefix/*' | true
    'prefixa'  | '/*'        | false
    'a.suffix' | '/*.suffix' | true
    '.suffix'  | '/*.suffix' | true
    'suffix'   | '/*'        | false
  }
}
