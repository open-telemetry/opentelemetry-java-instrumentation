import groovy.servlet.AbstractHttpServlet

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class TestServlet {

  static class Sync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (req.getParameter("error") != null) {
        throw new RuntimeException("some sync error")
      }
      resp.writer.print("Hello Sync")
    }
  }
}
