/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.dispatch;

import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.TomcatServlet3Test;

public abstract class TomcatDispatchTest extends TomcatServlet3Test {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath(getContextPath() + "/dispatch");
  }
}
