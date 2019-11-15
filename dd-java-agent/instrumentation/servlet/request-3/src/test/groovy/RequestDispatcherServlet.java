import java.io.IOException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestDispatcherServlet {
  /* There's something about the getRequestDispatcher call that breaks horribly when these classes
   * are written in groovy.
   */

  @WebServlet(asyncSupported = true)
  public static class Forward extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
        throws ServletException, IOException {
      final String target = req.getServletPath().replace("/dispatch", "");
      final ServletContext context = getServletContext();
      final RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      dispatcher.forward(req, resp);
    }
  }

  @WebServlet(asyncSupported = true)
  public static class Include extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
        throws ServletException, IOException {
      final String target = req.getServletPath().replace("/dispatch", "");
      final ServletContext context = getServletContext();
      final RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      dispatcher.include(req, resp);
    }
  }
}
