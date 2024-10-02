/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.Router;

public class AbstractServletServerTest extends AbstractHttpServerTest<Server> {

  @Override
  protected Server setupServer() throws Exception {
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());

    webAppContext.setBaseResource(Resource.newSystemResource("servlet-ext-app"));

    Server jettyServer = new Server(port);
    jettyServer.setHandler(webAppContext);
    jettyServer.start();

    return jettyServer;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestException(false);
    options.setTestPathParam(true);
    options.setResponseCodeOnNonStandardHttpMethod(405);
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (HttpConstants._OTHER.equals(method)) {
            return getContextPath() + endpoint.getPath();
          }

          if (PATH_PARAM.equals(endpoint)) {
            return getContextPath() + "/path/{id}/param";
          } else if (NOT_FOUND.equals(endpoint)) {
            return getContextPath() + "/*";
          }

          return expectedHttpRoute(endpoint, method);
        });
  }

  // used from web.xml
  public static class TestApp extends Application {

    @Override
    public Restlet createRoot() {
      Router router = new Router(getContext());

      router.attach(SUCCESS.getPath(), RestletAppTestBase.SuccessResource.class);
      router.attach(REDIRECT.getPath(), RestletAppTestBase.RedirectResource.class);
      router.attach(ERROR.getPath(), RestletAppTestBase.ErrorResource.class);
      router.attach(EXCEPTION.getPath(), RestletAppTestBase.ExceptionResource.class);
      router.attach("/path/{id}/param", RestletAppTestBase.PathParamResource.class);
      router.attach(QUERY_PARAM.getPath(), RestletAppTestBase.QueryParamResource.class);
      router.attach(CAPTURE_HEADERS.getPath(), RestletAppTestBase.CaptureHeadersResource.class);
      router.attach(INDEXED_CHILD.getPath(), RestletAppTestBase.IndexedChildResource.class);

      return router;
    }
  }
}
