/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpserver;

import com.sun.net.httpserver.Filter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.httpserver.JavaServerTelemetry;
import io.opentelemetry.instrumentation.httpserver.JavaServerTelemetryBuilder;
import io.opentelemetry.instrumentation.httpserver.internal.JavaInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.Arrays;
import java.util.List;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class JavaSingletons {

  public static final List<Filter> FILTERS;

  static {
    CommonConfig config = AgentCommonConfig.get();

    JavaServerTelemetryBuilder serverBuilder =
        JavaServerTelemetry.builder(GlobalOpenTelemetry.get());
    JavaInstrumenterBuilderUtil.getServerBuilderExtractor().apply(serverBuilder).configure(config);
    JavaServerTelemetry serverTelemetry = serverBuilder.build();

    FILTERS = Arrays.asList(serverTelemetry.otelFilter(), new ResponseCustomizingFilter());
  }

  private JavaSingletons() {}
}
