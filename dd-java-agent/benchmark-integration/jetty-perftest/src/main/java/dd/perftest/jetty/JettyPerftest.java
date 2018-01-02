package dd.perftest.jetty;

import dd.perftest.Worker;
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

  public static void main(String[] args) throws Exception {
    servletContext.addServlet(PerfServlet.class, PATH);
    jettyServer.setHandler(servletContext);
    jettyServer.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
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
  @SuppressWarnings("serial")
  public static class PerfServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      if (request.getParameter("error") != null) {
        throw new RuntimeException("some sync error");
      }
      final String workVal = request.getParameter("workTimeMS");
      long workTimeMS = 0l;
      if (null != workVal) {
        workTimeMS = Long.parseLong(workVal);
      }
      if (workTimeMS > 0) {
        Worker.doWork(workTimeMS);
      }
      response.getWriter().print("Did " + workTimeMS + "ms of work.");
    }
  }
}
