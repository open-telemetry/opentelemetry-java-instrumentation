package io.opentelemetry.perftest.jetty;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.Trace;
import io.opentelemetry.perftest.Worker;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import javax.servlet.ServletException;
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

  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  public static void main(final String[] args) throws Exception {
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
                } catch (final Exception e) {
                  throw new IllegalStateException(e);
                }
              }
            });
  }

  @WebServlet
  public static class PerfServlet extends HttpServlet {
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws ServletException, IOException {
      if (request.getParameter("error") != null) {
        throw new RuntimeException("some sync error");
      }
      final String workVal = request.getParameter("workTimeMS");
      long workTimeMS = 0l;
      if (null != workVal) {
        workTimeMS = Long.parseLong(workVal);
      }
      scheduleWork(workTimeMS);
      response.getWriter().print("Did " + workTimeMS + "ms of work.");
    }

    @Trace
    private void scheduleWork(final long workTimeMS) {
      final Span span = TRACER.getCurrentSpan();
      if (span != null) {
        span.setAttribute("work-time", workTimeMS);
        span.setAttribute("info", "interesting stuff");
        span.setAttribute("additionalInfo", "interesting stuff");
      }
      if (workTimeMS > 0) {
        Worker.doWork(workTimeMS);
      }
    }
  }
}
