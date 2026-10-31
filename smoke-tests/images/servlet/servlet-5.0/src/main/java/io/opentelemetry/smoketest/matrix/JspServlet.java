/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JspServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
    String snippetScenario = req.getParameter("snippetScenario");
    if ("stream-forward".equals(snippetScenario)) {
      resp.setHeader("X-Snippet-Scenario", snippetScenario);
      resp.setContentType("text/html");
      resp.getOutputStream().write("<head>".getBytes(UTF_8));
    } else if ("async-reset".equals(snippetScenario)) {
      resp.setHeader("X-Snippet-Scenario", snippetScenario);
      resp.setContentType("text/html");
      resp.getWriter().write("<head>");
      AsyncContext asyncContext = req.startAsync();
      asyncContext.getResponse().resetBuffer();
      asyncContext.dispatch("/test.jsp");
      return;
    } else if ("error-dispatch".equals(snippetScenario)) {
      resp.setHeader("X-Snippet-Scenario", snippetScenario);
      resp.setContentType("text/html;charset=UTF-8");
      resp.setContentLength(1000);
      resp.getOutputStream().write("<head>".getBytes(UTF_8));
      req.setAttribute("snippet.error.sourceContentLength", resp.getHeader("Content-Length"));
      req.setAttribute("snippet.error.sourceCommitted", resp.isCommitted());
      throw new SnippetErrorException();
    }

    RequestDispatcher resultView = req.getRequestDispatcher("test.jsp");
    resultView.forward(req, resp);
  }
}
