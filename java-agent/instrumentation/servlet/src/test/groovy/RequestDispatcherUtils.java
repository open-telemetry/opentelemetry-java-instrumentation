import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class RequestDispatcherUtils {
  private final ServletRequest req;
  private final ServletResponse resp;
  private final ServletException toThrow;

  public RequestDispatcherUtils(final ServletRequest req, final ServletResponse resp) {
    this.req = req;
    this.resp = resp;
    toThrow = null;
  }

  public RequestDispatcherUtils(
      final ServletRequest req, final ServletResponse resp, final ServletException toThrow) {
    this.req = req;
    this.resp = resp;
    this.toThrow = toThrow;
  }

  /* RequestDispatcher can't be visible to groovy otherwise things break, so everything is
   * encapsulated in here where groovy doesn't need to access it.
   */

  void forward(final String target) throws ServletException, IOException {
    new TestContext().getRequestDispatcher(target).forward(req, resp);
  }

  void include(final String target) throws ServletException, IOException {
    new TestContext().getRequestDispatcher(target).include(req, resp);
  }

  void forwardNamed(final String target) throws ServletException, IOException {
    new TestContext().getNamedDispatcher(target).forward(req, resp);
  }

  void includeNamed(final String target) throws ServletException, IOException {
    new TestContext().getNamedDispatcher(target).include(req, resp);
  }

  class TestContext implements ServletContext {
    @Override
    public ServletContext getContext(final String s) {
      return null;
    }

    @Override
    public int getMajorVersion() {
      return 0;
    }

    @Override
    public int getMinorVersion() {
      return 0;
    }

    @Override
    public String getMimeType(final String s) {
      return null;
    }

    @Override
    public Set getResourcePaths(final String s) {
      return null;
    }

    @Override
    public URL getResource(final String s) throws MalformedURLException {
      return null;
    }

    @Override
    public InputStream getResourceAsStream(final String s) {
      return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String s) {
      return new TestDispatcher();
    }

    @Override
    public RequestDispatcher getNamedDispatcher(final String s) {
      return new TestDispatcher();
    }

    @Override
    public Servlet getServlet(final String s) throws ServletException {
      return null;
    }

    @Override
    public Enumeration getServlets() {
      return null;
    }

    @Override
    public Enumeration getServletNames() {
      return null;
    }

    @Override
    public void log(final String s) {}

    @Override
    public void log(final Exception e, final String s) {}

    @Override
    public void log(final String s, final Throwable throwable) {}

    @Override
    public String getRealPath(final String s) {
      return null;
    }

    @Override
    public String getServerInfo() {
      return null;
    }

    @Override
    public String getInitParameter(final String s) {
      return null;
    }

    @Override
    public Enumeration getInitParameterNames() {
      return null;
    }

    @Override
    public Object getAttribute(final String s) {
      return null;
    }

    @Override
    public Enumeration getAttributeNames() {
      return null;
    }

    @Override
    public void setAttribute(final String s, final Object o) {}

    @Override
    public void removeAttribute(final String s) {}

    @Override
    public String getServletContextName() {
      return null;
    }
  }

  class TestDispatcher implements RequestDispatcher {
    @Override
    public void forward(final ServletRequest servletRequest, final ServletResponse servletResponse)
        throws ServletException, IOException {
      if (toThrow != null) {
        throw toThrow;
      }
    }

    @Override
    public void include(final ServletRequest servletRequest, final ServletResponse servletResponse)
        throws ServletException, IOException {
      if (toThrow != null) {
        throw toThrow;
      }
    }
  }
}
