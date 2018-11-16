import groovy.servlet.AbstractHttpServlet

import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CountDownLatch

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
      def latch = new CountDownLatch(1)
      def context = req.startAsync()
      context.start {
        latch.await()
        resp.writer.print("Hello Async")
        context.complete()
      }
      latch.countDown()
    }
  }

  @WebServlet(asyncSupported = true)
  static class BlockingAsync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      def latch = new CountDownLatch(1)
      def context = req.startAsync()
      context.start {
        resp.writer.print("Hello BlockingAsync")
        context.complete()
        latch.countDown()
      }
      latch.await()
    }
  }

  @WebServlet(asyncSupported = true)
  static class DispatchSync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      req.startAsync().dispatch("/sync")
    }
  }

  @WebServlet(asyncSupported = true)
  static class DispatchAsync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      def context = req.startAsync()
      context.start {
        context.dispatch("/async")
      }
    }
  }

  @WebServlet(asyncSupported = true)
  static class DispatchRecursive extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (req.servletPath.equals("/recursive")) {
        resp.writer.print("Hello Recursive")
        return
      }
      def depth = Integer.parseInt(req.getParameter("depth"))
      if (depth > 0) {
        req.startAsync().dispatch("/dispatch/recursive?depth=" + (depth - 1))
      } else {
        req.startAsync().dispatch("/recursive")
      }
    }
  }

  @WebServlet(asyncSupported = true)
  static class FakeAsync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      def context = req.startAsync()
      resp.writer.print("Hello FakeAsync")
      context.complete()
    }
  }

}
