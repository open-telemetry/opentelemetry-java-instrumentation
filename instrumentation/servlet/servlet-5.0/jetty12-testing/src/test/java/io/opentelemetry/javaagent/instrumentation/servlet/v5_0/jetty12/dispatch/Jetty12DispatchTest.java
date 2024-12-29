/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12.dispatch;

import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12.Jetty12Servlet5Test;

abstract class Jetty12DispatchTest extends Jetty12Servlet5Test {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath(getContextPath() + "/dispatch");
  }
}
