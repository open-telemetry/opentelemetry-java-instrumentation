/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.TestServlet5;
import jakarta.servlet.Servlet;

public class JettyServlet5FakeAsyncTest extends JettyServlet5Test {
  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.FakeAsync.class;
  }
}
