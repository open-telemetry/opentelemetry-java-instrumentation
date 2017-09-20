package com.datadoghq.agent.integration.servlet

import groovy.servlet.AbstractHttpServlet

import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class TestServlet {

  @WebServlet
  static class Sync extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      resp.writer.print("Hello Sync")
    }
  }

  @WebServlet(asyncSupported = true)
  static class Async extends AbstractHttpServlet {
    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
      Thread initialThread = Thread.currentThread()
      def context = req.startAsync()
      context.start {
        assert Thread.currentThread() != initialThread
        resp.writer.print("Hello Async")
        context.complete()
      }
    }
  }
}
