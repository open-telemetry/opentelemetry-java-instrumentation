/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.tomcat;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.servlet.v5_0.ServletTelemetry;
import io.opentelemetry.instrumentation.servlet.v5_0.ServletTelemetryBuilder;
import io.opentelemetry.instrumentation.servlet.v5_0.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import jakarta.servlet.Filter;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

class ServletTestUtil {

  static Filter newFilter(OpenTelemetry openTelemetry) {
    ServletTelemetryBuilder builder =
        ServletTelemetry.builder(openTelemetry)
            .setRequestHeaders(AbstractHttpServerTest.TEST_HEADERS)
            .setResponseHeaders(AbstractHttpServerTest.TEST_HEADERS);
    Experimental.setRequestParameters(
        builder, IncludeExclude.builder().setIncluded(singletonList("test-parameter")).build());
    Experimental.setTraceIdRequestAttributeEnabled(builder, true);
    return builder.build().createFilter();
  }

  static void configureTomcat(OpenTelemetry openTelemetry, Context servletContext) {
    Filter filter = newFilter(openTelemetry);

    FilterDef filterDef = new FilterDef();
    filterDef.setFilterName("otel-filter");
    filterDef.setFilter(filter);
    servletContext.addFilterDef(filterDef);
    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName(filterDef.getFilterName());
    filterMap.addURLPatternDecoded("*");
    servletContext.addFilterMap(filterMap);
  }

  private ServletTestUtil() {}
}
