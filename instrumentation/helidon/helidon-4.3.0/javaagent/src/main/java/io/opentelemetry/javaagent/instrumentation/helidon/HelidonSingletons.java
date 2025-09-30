/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon;

import io.helidon.webserver.http.Filter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.helidon.HelidonTelemetry;
import io.opentelemetry.instrumentation.helidon.internal.HelidonInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.List;

public final class HelidonSingletons {

  public static final List<Filter> FILTERS;

  static {
    var serverBuilder = HelidonTelemetry.builder(GlobalOpenTelemetry.get());
    HelidonInstrumenterBuilderUtil.getServerBuilderExtractor()
        .apply(serverBuilder)
        .configure(AgentCommonConfig.get());
    var serverTelemetry = serverBuilder.build();

    FILTERS = List.of(serverTelemetry.createFilter(), new ResponseCustomizingFilter());
  }

  private HelidonSingletons() {}
}
