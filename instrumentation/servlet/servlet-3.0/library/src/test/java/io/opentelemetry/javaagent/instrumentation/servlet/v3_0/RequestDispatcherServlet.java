/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import java.io.IOException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestDispatcherServlet {

  @WebServlet(asyncSupported = true)
  public static class Forward extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      String target = req.getServletPath().replace("/dispatch", "");
      ServletContext context = getServletContext();
      RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      dispatcher.forward(req, resp);
    }
  }

  @WebServlet(asyncSupported = true)
  public static class Include extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      String target = req.getServletPath().replace("/dispatch", "");
      ServletContext context = getServletContext();
      RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      // for HTML test case, set the content type before calling include because
      // setContentType will be rejected if called inside include
      // check
      // https://docs.oracle.com/javaee/7/api/javax/servlet/RequestDispatcher.html#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-
      if ("/htmlPrintWriter".equals(target) || "/htmlServletOutputStream".equals(target)) {
        resp.setContentType("text/html");
      }
      dispatcher.include(req, resp);
    }
  }

  private RequestDispatcherServlet() {}
}
