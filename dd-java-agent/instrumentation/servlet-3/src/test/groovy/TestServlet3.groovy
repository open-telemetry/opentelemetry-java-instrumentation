import groovy.servlet.AbstractHttpServlet

import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class TestServlet3 {

  @WebServlet
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

  @WebServlet(asyncSupported = true)
  static class Async extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      def context = req.startAsync()
      context.start {
        resp.writer.print("Hello Async")
        context.complete()
      }
    }
  }
}
