/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import jakarta.servlet.Servlet;

class JettyServlet5FakeAsyncTest extends JettyServlet5Test {
  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.FakeAsync.class;
  }
}
