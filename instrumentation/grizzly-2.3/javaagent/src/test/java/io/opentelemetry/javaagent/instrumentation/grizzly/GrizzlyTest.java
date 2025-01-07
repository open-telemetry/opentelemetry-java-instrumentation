/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class GrizzlyTest extends AbstractHttpServerTest<HttpServer> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected HttpServer setupServer() throws Exception {
    HttpServer server = new HttpServer();
    NetworkListener listener = new NetworkListener("grizzly", "localhost", port);
    configureListener(listener);
    server.addListener(listener);
    ServerConfiguration config = server.getServerConfiguration();
    config.addHttpHandler(
        new HttpHandler() {
          @Override
          public void service(Request request, Response response) throws Exception {
            ServerEndpoint endpoint = ServerEndpoint.forPath(request.getDecodedRequestURI());
            controller(
                endpoint,
                () -> {
                  if (endpoint.equals(SUCCESS)) {
                    try {
                      response.getWriter().write(endpoint.getBody());
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  } else if (endpoint.equals(INDEXED_CHILD)) {
                    response.setStatus(endpoint.getStatus());
                    endpoint.collectSpanAttributes(request::getParameter);
                  } else if (endpoint.equals(QUERY_PARAM)) {
                    response.setStatus(endpoint.getStatus());
                    try {
                      response.getWriter().write(request.getQueryString());
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  } else if (endpoint.equals(REDIRECT)) {
                    try {
                      response.sendRedirect(endpoint.getBody());
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  } else if (endpoint.equals(ERROR)) {
                    try {
                      response.sendError(endpoint.getStatus(), endpoint.getBody());
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  } else if (endpoint.equals(NOT_FOUND)) {
                    response.setStatus(endpoint.getStatus());
                  } else if (endpoint.equals(EXCEPTION)) {
                    throw new IllegalArgumentException(EXCEPTION.getBody());
                  } else {
                    throw new IllegalStateException("unexpected endpoint " + endpoint);
                  }
                  return response;
                });
          }
        },
        "/");

    server.start();
    return server;
  }

  protected abstract void configureListener(NetworkListener listener);

  @Override
  protected void stopServer(HttpServer server) {
    server.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(HTTP_ROUTE);
          return attributes;
        });
    options.setTestCaptureHttpHeaders(false);
    options.setHasResponseCustomizer(serverEndpoint -> true);
    options.setVerifyServerSpanEndTime(false); // fails for redirect test
    options.setTestErrorBody(false);
    options.setExpectedException(new IllegalArgumentException(EXCEPTION.getBody()));
  }
}
