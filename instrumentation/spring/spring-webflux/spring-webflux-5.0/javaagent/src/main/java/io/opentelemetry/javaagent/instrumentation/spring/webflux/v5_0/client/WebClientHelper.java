/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

public final class WebClientHelper {

  private static final SpringWebfluxTelemetry INSTRUMENTATION =
      SpringWebfluxTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedClientRequestHeaders(CommonConfig.get().getClientRequestHeaders())
          .setCapturedClientResponseHeaders(CommonConfig.get().getClientResponseHeaders())
          .addClientAttributesExtractor(
              PeerServiceAttributesExtractor.create(
                  new WebClientNetAttributesGetter(), CommonConfig.get().getPeerServiceMapping()))
          .setCaptureExperimentalSpanAttributes(
              InstrumentationConfig.get()
                  .getBoolean(
                      "otel.instrumentation.spring-webflux.experimental-span-attributes", false))
          .build();

  public static void addFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    INSTRUMENTATION.addClientTracingFilter(exchangeFilterFunctions);
  }

  private WebClientHelper() {}
}
