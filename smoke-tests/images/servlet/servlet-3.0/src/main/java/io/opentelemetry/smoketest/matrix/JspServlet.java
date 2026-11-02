/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.AsyncContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JspServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
    String snippetScenario = req.getParameter("snippetScenario");
    if ("async-reset-stream".equals(snippetScenario)) {
      resetAsyncResponse(req, resp, false);
      return;
    } else if ("async-reset-writer".equals(snippetScenario)) {
      resetAsyncResponse(req, resp, true);
      return;
    } else if ("stream-forward".equals(snippetScenario)) {
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

  private static void resetAsyncResponse(
      HttpServletRequest req, HttpServletResponse resp, boolean useWriter) throws IOException {
    resp.setContentType("text/html;charset=ISO-8859-1");
    resp.setContentLength(1000);
    PrintWriter originalWriter = resp.getWriter();
    originalWriter.write("<head>discarded prefix");
    String sourceContentLength = resp.getHeader("Content-Length");
    boolean sourceCommitted = resp.isCommitted();
    AsyncContext asyncContext = req.startAsync();
    try {
      HttpServletResponse rawResponse = (HttpServletResponse) asyncContext.getResponse();
      if (rawResponse == resp) {
        throw new IllegalStateException("Expected the raw async response");
      }
      PrintWriter originalNativeWriter = rawResponse.getWriter();
      rawResponse.reset();
      resp.setContentType("text/html;charset=UTF-8");
      resp.setHeader("X-Snippet-Scenario", useWriter ? "async-reset-writer" : "async-reset-stream");
      resp.setHeader("X-Snippet-Source-Content-Length", sourceContentLength);
      resp.setHeader("X-Snippet-Source-Committed", Boolean.toString(sourceCommitted));
      String body = "<html><head></head><body>Replacement café</body></html>";
      resp.setContentLength(body.getBytes(UTF_8).length);
      if (useWriter) {
        PrintWriter writer = resp.getWriter();
        try {
          rawResponse.getOutputStream();
          throw new IllegalStateException("Writer acquisition did not restore native ownership");
        } catch (IllegalStateException e) {
          if (!"WRITER".equals(e.getMessage())) {
            throw e;
          }
        }
        resp.setHeader(
            "X-Snippet-Native-Writer-Changed",
            Boolean.toString(rawResponse.getWriter() != originalNativeWriter));
        resp.setHeader("X-Snippet-Writer-Reused", Boolean.toString(resp.getWriter() == writer));
        writer.write(body);
      } else {
        ServletOutputStream stream = resp.getOutputStream();
        try {
          resp.getWriter();
          throw new IllegalStateException(
              "Cached writer acquisition did not reject stream ownership");
        } catch (IllegalStateException e) {
          if (!"STREAM".equals(e.getMessage())) {
            throw e;
          }
          resp.setHeader("X-Snippet-Writer-Rejected", "true");
        }
        stream.write(body.getBytes(UTF_8));
      }
    } finally {
      asyncContext.complete();
    }
  }
}
