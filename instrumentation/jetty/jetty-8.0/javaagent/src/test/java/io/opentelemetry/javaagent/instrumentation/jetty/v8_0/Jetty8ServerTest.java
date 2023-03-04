/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;

import com.google.common.collect.Sets;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.junit.jupiter.api.extension.RegisterExtension;
import spock.lang.Shared;

public class Jetty8ServerTest extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  static ErrorHandler errorHandler =
      new ErrorHandler() {
        @Override
        protected void handleErrorPage(
            HttpServletRequest request, Writer writer, int code, String message)
            throws IOException {
          Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception");
          String errorMsg = th != null ? th.getMessage() : message;
          if (errorMsg != null) {
            writer.write(errorMsg);
          }
        }
      };

  @Shared static final TestHandler testHandler = new TestHandler();

  @Override
  protected Server setupServer() {
    Server server = new Server(port);
    server.setHandler(testHandler);
    server.addBean(errorHandler);
    try {
      server.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return server;
  }

  @Override
  protected void stopServer(Server server) {
    try {
      server.stop();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        unused ->
            Sets.difference(
                DEFAULT_HTTP_ATTRIBUTES, Collections.singleton(SemanticAttributes.HTTP_ROUTE)));
    options.setHasResponseSpan(endpoint -> endpoint == REDIRECT || endpoint == ERROR);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));
    super.configure(options);
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parent, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        span.hasName("Response.sendRedirect").hasKind(SpanKind.INTERNAL).hasParent(parent);
        break;
      case ERROR:
        span.hasName("Response.sendError").hasKind(SpanKind.INTERNAL).hasParent(parent);
        break;
      default:
        break;
    }
    return span;
  }

  static void handleRequest(Request request, HttpServletResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.forPath(request.getRequestURI());
    controller(
        endpoint,
        () -> {
          try {
            return extracted(request, response, endpoint);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static HttpServletResponse extracted(
      Request request, HttpServletResponse response, ServerEndpoint endpoint) throws IOException {
    response.setContentType("text/plain");
    switch (endpoint) {
      case SUCCESS:
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(endpoint.getBody());
        break;
      case QUERY_PARAM:
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(request.getQueryString());
        break;
      case REDIRECT:
        response.sendRedirect(endpoint.getBody());
        break;
      case ERROR:
        response.sendError(endpoint.getStatus(), endpoint.getBody());
        break;
      case CAPTURE_HEADERS:
        response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"));
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(endpoint.getBody());
        break;
      case EXCEPTION:
        throw new IllegalStateException(endpoint.getBody());
      case INDEXED_CHILD:
        INDEXED_CHILD.collectSpanAttributes(name -> request.getParameter(name));
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(endpoint.getBody());
        break;
      default:
        response.setStatus(NOT_FOUND.getStatus());
        response.getWriter().print(NOT_FOUND.getBody());
        break;
    }
    return response;
  }

  private static class TestHandler extends AbstractHandler {
    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException {
      if (baseRequest.getDispatcherType() != DispatcherType.ERROR) {
        handleRequest(baseRequest, response);
        baseRequest.setHandled(true);
      } else {
        errorHandler.handle(target, baseRequest, baseRequest, response);
      }
    }
  }
}
