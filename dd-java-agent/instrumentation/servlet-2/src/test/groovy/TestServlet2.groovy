import groovy.servlet.AbstractHttpServlet

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class TestServlet2 {

  static class Sync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (req.getParameter("error") != null) {
        throw new RuntimeException("some sync error")
      }
      if (req.getParameter("non-throwing-error") != null) {
        resp.sendError(500, "some sync error")
        return
      }
      resp.writer.print("Hello Sync")
    }
  }
}
