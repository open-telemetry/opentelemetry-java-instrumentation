/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpserver;

import static java.util.Arrays.asList;

import com.sun.net.httpserver.Filter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.javahttpserver.JavaHttpServerTelemetry;
import io.opentelemetry.instrumentation.javahttpserver.JavaHttpServerTelemetryBuilder;
import io.opentelemetry.instrumentation.javahttpserver.internal.JavaHttpServerInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.List;

public final class JavaHttpServerSingletons {

  private static final List<Filter> instrumentationFilters;

  static {
    CommonConfig config = AgentCommonConfig.get();

    JavaHttpServerTelemetryBuilder serverBuilder =
        JavaHttpServerTelemetry.builder(GlobalOpenTelemetry.get());
    JavaHttpServerInstrumenterBuilderUtil.getServerBuilderExtractor()
        .apply(serverBuilder)
        .configure(config);
    JavaHttpServerTelemetry serverTelemetry = serverBuilder.build();

    instrumentationFilters =
        asList(serverTelemetry.createFilter(), new ResponseCustomizingFilter());
  }

  public static List<Filter> instrumentationFilters() {
    return instrumentationFilters;
  }

  private JavaHttpServerSingletons() {}
}
