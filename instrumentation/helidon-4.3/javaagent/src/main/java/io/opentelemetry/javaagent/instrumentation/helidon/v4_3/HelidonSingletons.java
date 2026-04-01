/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon.v4_3;

import io.helidon.webserver.http.Filter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.helidon.v4_3.HelidonTelemetry;
import io.opentelemetry.instrumentation.helidon.v4_3.internal.HelidonInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.List;

public final class HelidonSingletons {

  private static final List<Filter> filters;

  static {
    var serverBuilder = HelidonTelemetry.builder(GlobalOpenTelemetry.get());
    HelidonInstrumenterBuilderUtil.getServerBuilderExtractor()
        .apply(serverBuilder)
        .configure(AgentCommonConfig.get());
    var serverTelemetry = serverBuilder.build();

    filters = List.of(serverTelemetry.createFilter(), new ResponseCustomizingFilter());
  }

  public static List<Filter> filters() {
    return filters;
  }

  private HelidonSingletons() {}
}
