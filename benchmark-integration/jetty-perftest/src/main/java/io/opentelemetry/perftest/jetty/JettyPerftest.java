/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.perftest.jetty;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.perftest.Worker;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
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

  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto");

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
      String workVal = request.getParameter("workTimeMS");
      long workTimeMS = 0l;
      if (null != workVal) {
        workTimeMS = Long.parseLong(workVal);
      }
      scheduleWork(workTimeMS);
      response.getWriter().print("Did " + workTimeMS + "ms of work.");
    }

    private void scheduleWork(long workTimeMS) {
      Span span = TRACER.spanBuilder("work").startSpan();
      try (Scope scope = currentContextWith(span)) {
        if (span != null) {
          span.setAttribute("work-time", workTimeMS);
          span.setAttribute("info", "interesting stuff");
          span.setAttribute("additionalInfo", "interesting stuff");
        }
        if (workTimeMS > 0) {
          Worker.doWork(workTimeMS);
        }
        span.end();
      }
    }
  }
}
