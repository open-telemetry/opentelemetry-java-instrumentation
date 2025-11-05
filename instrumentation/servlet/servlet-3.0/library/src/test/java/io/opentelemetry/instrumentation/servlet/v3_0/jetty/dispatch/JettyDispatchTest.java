/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.jetty.dispatch;

import io.opentelemetry.instrumentation.servlet.v3_0.jetty.JettyServlet3Test;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;

abstract class JettyDispatchTest extends JettyServlet3Test {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath(getContextPath() + "/dispatch");
  }
}
