/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AsyncStreamSnippetServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String STREAM_ATTRIBUTE =
      AsyncStreamSnippetServlet.class.getName() + ".stream";
  private static final byte[] BODY =
      "<html><head><title>Async stream injection</title></head><body>done</body></html>"
          .getBytes(UTF_8);

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (request.getDispatcherType() == DispatcherType.REQUEST) {
      response.setContentType("text/html;charset=UTF-8");
      request.setAttribute(STREAM_ATTRIBUTE, response.getOutputStream());
      AsyncContext asyncContext = request.startAsync();
      asyncContext.dispatch("/async-stream-snippet/write");
      return;
    }

    ServletOutputStream stream = (ServletOutputStream) request.getAttribute(STREAM_ATTRIBUTE);
    stream.write(BODY);
    stream.flush();
  }
}
