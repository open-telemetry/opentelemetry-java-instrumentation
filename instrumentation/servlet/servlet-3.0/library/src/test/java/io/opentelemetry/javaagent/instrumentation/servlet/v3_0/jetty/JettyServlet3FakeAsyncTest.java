/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.jetty;

import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.TestServlet3;
import javax.servlet.Servlet;

class JettyServlet3FakeAsyncTest extends JettyServlet3Test {
  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet3.FakeAsync.class;
  }
}
