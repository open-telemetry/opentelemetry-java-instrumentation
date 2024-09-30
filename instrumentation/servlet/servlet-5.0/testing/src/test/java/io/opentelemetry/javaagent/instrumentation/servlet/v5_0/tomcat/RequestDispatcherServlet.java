/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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
      // https://jakarta.ee/specifications/platform/9/apidocs/jakarta/servlet/requestdispatcher#forward-jakarta.servlet.ServletRequest-jakarta.servlet.ServletResponse-
      if ("/htmlPrintWriter".equals(target) || "/htmlServletOutputStream".equals(target)) {
        resp.setContentType("text/html");
      }
      dispatcher.include(req, resp);
    }
  }

  private RequestDispatcherServlet() {}
}
