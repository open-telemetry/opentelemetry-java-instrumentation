/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import javax.servlet.Filter;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class ServletTestUtil {

  public static Filter newFilter(OpenTelemetry openTelemetry) {
    ServletTelemetryBuilder builder =
        ServletTelemetry.builder(openTelemetry)
            .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .setCapturedRequestParameters(singletonList("test-parameter"));
    Experimental.addTraceIdRequestAttribute(builder, true);
    return builder.build().newFilter();
  }

  public static void configureTomcat(OpenTelemetry openTelemetry, Context servletContext) {
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
