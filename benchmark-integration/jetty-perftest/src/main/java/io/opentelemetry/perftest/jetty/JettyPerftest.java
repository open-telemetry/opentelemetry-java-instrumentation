/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.perftest.jetty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.perftest.Worker;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class JettyPerftest {

  private static final int PORT = 8080;
  private static final String PATH = "/work";
  private static final Server jettyServer = new Server(PORT);
  private static final ServletContextHandler servletContext = new ServletContextHandler();

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  public static void main(String[] args) throws Exception {
    servletContext.addServlet(PerfServlet.class, PATH);
    jettyServer.setHandler(servletContext);
    jettyServer.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                try {
                  jettyServer.stop();
                  jettyServer.destroy();
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              }
            });
  }

  @WebServlet
  public static class PerfServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      if (request.getParameter("error") != null) {
        throw new RuntimeException("some sync error");
      }
      String workVal = request.getParameter("workTimeMillis");
      long workTimeMillis = 0L;
      if (null != workVal) {
        workTimeMillis = Long.parseLong(workVal);
      }
      scheduleWork(workTimeMillis);
      response.getWriter().print("Did " + workTimeMillis + "ms of work.");
    }

    private void scheduleWork(long workTimeMillis) {
      Span span = tracer.spanBuilder("work").startSpan();
      try (Scope scope = span.makeCurrent()) {
        if (span != null) {
          span.setAttribute("work-time", workTimeMillis);
          span.setAttribute("info", "interesting stuff");
          span.setAttribute("additionalInfo", "interesting stuff");
        }
        if (workTimeMillis > 0) {
          Worker.doWork(workTimeMillis);
        }
        span.end();
      }
    }
  }
}
