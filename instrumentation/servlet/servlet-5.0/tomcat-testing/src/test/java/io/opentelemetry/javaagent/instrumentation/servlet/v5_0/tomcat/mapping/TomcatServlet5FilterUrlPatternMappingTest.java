/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.mapping;

import org.apache.catalina.Context;

class TomcatServlet5FilterUrlPatternMappingTest extends TomcatServlet5FilterMappingTest {
  @Override
  protected void setupServlets(Context context) throws Exception {
    addFilter(context, "/*", FirstFilter.class);
    addFilter(context, "/prefix/*", TestFilter.class);
    addFilter(context, "*.suffix", TestFilter.class);
    addFilter(context, "/*", LastFilter.class);
  }
}
