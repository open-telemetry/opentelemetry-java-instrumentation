/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxTracing;
import java.util.List;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

public class WebClientHelper {

  private static final SpringWebfluxTracing INSTRUMENTATION =
      SpringWebfluxTracing.create(GlobalOpenTelemetry.get());

  public static void addFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    INSTRUMENTATION.addClientTracingFilter(exchangeFilterFunctions);
  }
}
