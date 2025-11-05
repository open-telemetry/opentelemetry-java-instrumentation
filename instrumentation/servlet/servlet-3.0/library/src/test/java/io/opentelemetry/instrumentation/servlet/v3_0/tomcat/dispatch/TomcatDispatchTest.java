/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.tomcat.dispatch;

import io.opentelemetry.instrumentation.servlet.v3_0.tomcat.TomcatServlet3Test;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;

abstract class TomcatDispatchTest extends TomcatServlet3Test {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath(getContextPath() + "/dispatch");
  }
}
