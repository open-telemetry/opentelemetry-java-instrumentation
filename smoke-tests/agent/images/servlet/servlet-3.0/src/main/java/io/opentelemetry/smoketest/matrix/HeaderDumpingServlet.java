/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HeaderDumpingServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    PrintWriter response = resp.getWriter();
    Enumeration<String> headerNames = req.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      response.write(headerName + ": ");

      List<String> headers = Collections.list(req.getHeaders(headerName));
      if (headers.size() == 1) {
        response.write(headers.get(0));
      } else {
        response.write("[");
        for (String header : headers) {
          response.write("  " + header + ",\n");
        }
        response.write("]");
      }
      response.write("\n");
    }

    response.flush();
  }
}
