/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Simple servlet for testing web.xml-based deployment. This servlet is intended to be loaded by
 * Tomcat's WebappClassLoader (from WEB-INF/classes) to reproduce classloader-related issues with
 * the javaagent instrumentation.
 */
public class WebXmlTestServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write("OK");
  }
}
