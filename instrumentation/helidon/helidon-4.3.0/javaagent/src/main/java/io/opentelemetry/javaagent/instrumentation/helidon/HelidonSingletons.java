/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon;

import java.util.Arrays;
import java.util.List;

import io.helidon.webserver.http.HttpFeature;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.helidon.HelidonTelemetry;
import io.opentelemetry.instrumentation.helidon.internal.HelidonInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class HelidonSingletons {

  public static final List<HttpFeature> FILTERS;

  static {
    var config = AgentCommonConfig.get();

    var serverBuilder = HelidonTelemetry.builder(GlobalOpenTelemetry.get());
    HelidonInstrumenterBuilderUtil.getServerBuilderExtractor()
        .apply(serverBuilder)
        .configure(config);
    var serverTelemetry = serverBuilder.build();

    FILTERS = Arrays.asList(serverTelemetry, r -> r.addFilter(new ResponseCustomizingFilter()));
  }

  private HelidonSingletons() {}
}
