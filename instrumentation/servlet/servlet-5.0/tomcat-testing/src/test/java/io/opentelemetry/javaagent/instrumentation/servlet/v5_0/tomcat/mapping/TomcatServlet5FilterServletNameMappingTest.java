/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.mapping;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

class TomcatServlet5FilterServletNameMappingTest extends TomcatServlet5FilterMappingTest {
  @Override
  protected void setupServlets(Context context) throws Exception {
    Tomcat.addServlet(context, "prefix-servlet", new DefaultServlet());
    context.addServletMappingDecoded("/prefix/*", "prefix-servlet");
    Tomcat.addServlet(context, "suffix-servlet", new DefaultServlet());
    context.addServletMappingDecoded("*.suffix", "suffix-servlet");

    addFilter(context, "/*", FirstFilter.class);
    addFilterWithServletName(context, "prefix-servlet", TestFilter.class);
    addFilterWithServletName(context, "suffix-servlet", TestFilter.class);
    addFilterWithServletName(context, "prefix-servlet", LastFilter.class);
    addFilterWithServletName(context, "suffix-servlet", LastFilter.class);
  }
}
