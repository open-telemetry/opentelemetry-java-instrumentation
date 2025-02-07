/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpserver;

import java.util.Arrays;
import java.util.List;

import com.sun.net.httpserver.Filter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.httpserver.JdkServerTelemetry;
import io.opentelemetry.instrumentation.httpserver.JdkServerTelemetryBuilder;
import io.opentelemetry.instrumentation.httpserver.internal.JdkInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class JdkSingletons {

  public static final List<Filter> SERVER_DECORATOR;

  static {
    CommonConfig config = AgentCommonConfig.get();

    JdkServerTelemetryBuilder serverBuilder = JdkServerTelemetry.builder(GlobalOpenTelemetry.get());
    JdkInstrumenterBuilderUtil.getServerBuilderExtractor().apply(serverBuilder).configure(config);
    JdkServerTelemetry serverTelemetry = serverBuilder.build();

    SERVER_DECORATOR = Arrays.asList(serverTelemetry.otelFilter(), new ResponseCustomizingFilter());
  }

  private JdkSingletons() {}
}
