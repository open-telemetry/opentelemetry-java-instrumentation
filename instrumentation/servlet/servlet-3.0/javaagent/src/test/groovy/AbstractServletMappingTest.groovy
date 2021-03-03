/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import javax.servlet.Servlet
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import spock.lang.Unroll

abstract class AbstractServletMappingTest<SERVER, CONTEXT> extends AgentInstrumentationSpecification implements HttpServerTestTrait<SERVER> {

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

  Request.Builder request(HttpUrl url, String method, RequestBody body) {
    return new Request.Builder()
      .url(url)
      .method(method, body)
  }

  @Unroll
  def "test path #path"() {
    setup:
    def url = HttpUrl.get(address.resolve(path)).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()

    expect:
    response.code() == success ? 200 : 404

    and:
    def spanCount = success ? 1 : 2
    assertTraces(1) {
      trace(0, spanCount) {
        span(0) {
          name getContextPath() + spanName
          kind SpanKind.SERVER
          errored !success
        }
        if (!success) {
          span(1) {
          }
        }
      }
    }

    where:
    path        | spanName    | success
    'prefix'    | '/prefix/*' | true
    'prefix/'   | '/prefix/*' | true
    'prefix/a'  | '/prefix/*' | true
    'prefixa'   | '/*'        | false
    'a.suffix'  | '/*.suffix' | true
    '.suffix'   | '/*.suffix' | true
    'suffix'    | '/*'        | false
  }
}
