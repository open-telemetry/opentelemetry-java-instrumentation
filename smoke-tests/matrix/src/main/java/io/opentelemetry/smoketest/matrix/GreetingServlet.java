/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GreetingServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = (req.getContextPath() + "/headers").replace("//", "/");
    URL url = new URL("http", "localhost", req.getLocalPort(), path);
    URLConnection urlConnection = url.openConnection();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (InputStream remoteInputStream = urlConnection.getInputStream()) {
      long bytesRead = transfer(remoteInputStream, buffer);
      String responseBody = buffer.toString("UTF-8");
      ServletOutputStream outputStream = resp.getOutputStream();
      outputStream.print(
          bytesRead + " bytes read by " + urlConnection.getClass().getName() + "\n" + responseBody);
      outputStream.flush();
    }
  }

  // We have to run on Java 8, so no Java 9 stream transfer goodies for us.
  private long transfer(InputStream from, OutputStream to) throws IOException {
    Objects.requireNonNull(to, "out");
    long transferred = 0;
    byte[] buffer = new byte[65535];
    int read;
    while ((read = from.read(buffer, 0, buffer.length)) >= 0) {
      to.write(buffer, 0, read);
      transferred += read;
    }
    return transferred;
  }
}
