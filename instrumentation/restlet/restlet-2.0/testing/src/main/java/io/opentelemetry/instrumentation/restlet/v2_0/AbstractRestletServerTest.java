/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

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
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.lang.reflect.Method;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.routing.Redirector;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.routing.VirtualHost;

public class AbstractRestletServerTest extends AbstractHttpServerTest<Component> {

  protected Component component;
  protected VirtualHost host;

  @Override
  protected Component setupServer() throws Exception {
    component = new Component();
    host = component.getDefaultHost();
    setupServer(component);
    setupRouting();

    component.start();

    return component;
  }

  protected void setupServer(Component component) {
    component.getServers().add(Protocol.HTTP, port);
  }

  @Override
  protected void stopServer(Component component) throws Exception {
    component.stop();
  }

  private void attachAndWrap(ServerEndpoint endpoint, Restlet restlet) {
    attachAndWrap(endpoint.getPath(), restlet);
  }

  private void attachAndWrap(String path, Restlet restlet) {
    attach(path, wrapRestlet(restlet, path));
  }

  // org.restlet.routing.Route is deprecated in 2.0 but not deprecated in later versions
  @SuppressWarnings("deprecation")
  private org.restlet.routing.Route attach(String path, Restlet restlet) {
    try {
      // return type is different in latest version
      Method method = VirtualHost.class.getMethod("attach", String.class, Restlet.class);
      return (org.restlet.routing.Route) method.invoke(host, path, restlet);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  // org.restlet.routing.Route is deprecated in 2.0 but not deprecated in later versions
  @SuppressWarnings("deprecation")
  protected void attach(Restlet restlet) {
    try {
      // return type is different in latest version
      Method method = VirtualHost.class.getMethod("attach", Restlet.class);
      method.invoke(host, restlet);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static Template getTemplate(Object route) {
    try {
      Method getTemplate = route.getClass().getMethod("getTemplate");
      return (Template) getTemplate.invoke(route);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  protected void setupRouting() {
    Restlet defaultRouter = wrapRestlet(new Router(host.getContext()), "/*");
    getTemplate(attach("/", defaultRouter)).setMatchingMode(Template.MODE_STARTS_WITH);

    attachAndWrap(
        SUCCESS,
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                SUCCESS,
                () -> {
                  response.setEntity(SUCCESS.getBody(), MediaType.TEXT_PLAIN);
                  response.setStatus(Status.valueOf(SUCCESS.getStatus()), SUCCESS.getBody());
                });
          }
        });

    attachAndWrap(
        REDIRECT,
        new Redirector(Context.getCurrent(), REDIRECT.getBody(), Redirector.MODE_CLIENT_FOUND) {
          @Override
          public void handle(Request request, Response response) {
            super.handle(request, response);
            controller(REDIRECT, () -> {});
          }
        });

    attachAndWrap(
        ERROR,
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                ERROR,
                () -> response.setStatus(Status.valueOf(ERROR.getStatus()), ERROR.getBody()));
          }
        });

    attachAndWrap(
        EXCEPTION,
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                EXCEPTION,
                () -> {
                  throw new IllegalStateException(EXCEPTION.getBody());
                });
          }
        });

    attachAndWrap(
        QUERY_PARAM,
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                QUERY_PARAM,
                () -> {
                  response.setEntity(QUERY_PARAM.getBody(), MediaType.TEXT_PLAIN);
                  response.setStatus(
                      Status.valueOf(QUERY_PARAM.getStatus()), QUERY_PARAM.getBody());
                });
          }
        });

    attachAndWrap(
        "/path/{id}/param",
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                PATH_PARAM,
                () -> {
                  response.setEntity(PATH_PARAM.getBody(), MediaType.TEXT_PLAIN);
                  response.setStatus(Status.valueOf(PATH_PARAM.getStatus()), PATH_PARAM.getBody());
                });
          }
        });

    attachAndWrap(
        "/captureHeaders",
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                CAPTURE_HEADERS,
                () -> {
                  RestletAppTestBase.handleCaptureHeaders(request, response);

                  response.setEntity(CAPTURE_HEADERS.getBody(), MediaType.TEXT_PLAIN);
                  response.setStatus(
                      Status.valueOf(CAPTURE_HEADERS.getStatus()), CAPTURE_HEADERS.getBody());
                });
          }
        });

    attachAndWrap(
        INDEXED_CHILD,
        new Restlet() {
          @Override
          public void handle(Request request, Response response) {
            controller(
                INDEXED_CHILD,
                () -> {
                  INDEXED_CHILD.collectSpanAttributes(
                      name -> request.getOriginalRef().getQueryAsForm().getFirstValue(name));
                  response.setStatus(Status.valueOf(INDEXED_CHILD.getStatus()));
                });
          }
        });
  }

  protected Restlet wrapRestlet(Restlet restlet, String path) {
    return restlet;
  }

  protected String notFoundRoute() {
    return "/*";
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestPathParam(true);
    options.setTestErrorBody(false);
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (HttpConstants._OTHER.equals(method)) {
            return getContextPath() + endpoint.getPath();
          }

          if (PATH_PARAM.equals(endpoint)) {
            return getContextPath() + "/path/{id}/param";
          } else if (NOT_FOUND.equals(endpoint)) {
            return getContextPath() + notFoundRoute();
          }

          return expectedHttpRoute(endpoint, method);
        });
  }
}
