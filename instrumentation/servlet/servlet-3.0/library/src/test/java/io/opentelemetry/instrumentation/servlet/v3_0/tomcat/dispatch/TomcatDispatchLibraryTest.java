/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.tomcat.dispatch;

import io.opentelemetry.instrumentation.servlet.v3_0.tomcat.TomcatServlet3LibraryTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;

abstract class TomcatDispatchLibraryTest extends TomcatServlet3LibraryTest {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath(getContextPath() + "/dispatch");
  }
}
