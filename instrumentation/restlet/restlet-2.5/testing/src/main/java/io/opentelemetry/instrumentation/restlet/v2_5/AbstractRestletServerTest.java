/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_5;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import org.junit.jupiter.api.Test;
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

  @Test
  void serverSpanHasVersionedScope() {
    assertThat(client.execute(request(SUCCESS, "GET")).aggregate().join().status().code())
        .isEqualTo(SUCCESS.getStatus());

    testing().waitAndAssertTraces(trace -> {
      InstrumentationScopeInfo scope = trace.getSpan(0).getInstrumentationScopeInfo();
      assertThat(scope.getName()).isEqualTo("io.opentelemetry.restlet-2.5");
      assertThat(scope.getVersion()).isNotBlank();
    });
  }

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
    host.attach(path, wrapRestlet(restlet, path));
  }

  protected void attach(Restlet restlet) {
    host.attach(restlet);
  }

  protected void setupRouting() {
    Restlet defaultRouter = wrapRestlet(new Router(host.getContext()), "/*");
    host.attach("/", defaultRouter).getTemplate().setMatchingMode(Template.MODE_STARTS_WITH);

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
                  response.setEntity(INDEXED_CHILD.getBody(), MediaType.TEXT_PLAIN);
                  response.setStatus(
                      Status.valueOf(INDEXED_CHILD.getStatus()), INDEXED_CHILD.getBody());
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
    options.setExpectedHttpRoute((endpoint, method) -> {
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
