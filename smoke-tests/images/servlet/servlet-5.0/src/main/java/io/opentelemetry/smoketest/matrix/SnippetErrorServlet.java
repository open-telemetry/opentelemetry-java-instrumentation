/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SnippetErrorServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String ERROR_BODY =
      "<!doctype html>\n"
          + "<html>\n"
          + "  <head>\n"
          + "    <title>Error page</title>\n"
          + "  </head>\n"
          + "  <body>Error page</body>\n"
          + "</html>\n";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    resp.setContentType("text/html;charset=UTF-8");
    resp.setContentLength(ERROR_BODY.getBytes(UTF_8).length);
    resp.setHeader("X-Snippet-Dispatcher", req.getDispatcherType().name());
    resp.getWriter().write(ERROR_BODY);
  }
}
