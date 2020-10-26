/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.AUTH_REQUIRED
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import io.opentelemetry.instrumentation.test.base.HttpServerTest
import javax.servlet.Servlet
import okhttp3.Request
import okhttp3.RequestBody

abstract class AbstractServlet3Test<SERVER, CONTEXT> extends HttpServerTest<SERVER> {
  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port/$context/")
  }

  // FIXME: Add authentication tests back in...
//  @Shared
//  protected String user = "user"
//  @Shared
//  protected String pass = "password"

  abstract String getContext()

  Class<Servlet> servlet = servlet()

  abstract Class<Servlet> servlet()

  abstract void addServlet(CONTEXT context, String path, Class<Servlet> servlet)

  protected void setupServlets(CONTEXT context) {
    def servlet = servlet()

    addServlet(context, SUCCESS.path, servlet)
    addServlet(context, QUERY_PARAM.path, servlet)
    addServlet(context, ERROR.path, servlet)
    addServlet(context, EXCEPTION.path, servlet)
    addServlet(context, REDIRECT.path, servlet)
    addServlet(context, AUTH_REQUIRED.path, servlet)
  }

  protected ServerEndpoint lastRequest

  @Override
  Request.Builder request(ServerEndpoint uri, String method, RequestBody body) {
    lastRequest = uri
    super.request(uri, method, body)
  }

  // Simple class name plus method name of the entry point of the given servlet container.
  // "Entry point" here means the first filter or servlet that accepts incoming requests.
  // This will serve as a default name of the SERVER span created for this request.
  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    "HttpServlet.service"
  }
}
