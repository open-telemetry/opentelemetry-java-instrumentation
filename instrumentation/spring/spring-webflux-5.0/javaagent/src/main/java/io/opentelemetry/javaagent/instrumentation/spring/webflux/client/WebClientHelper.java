/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.client.internal.SpringWebfluxNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

public final class WebClientHelper {

  private static final SpringWebfluxTelemetry INSTRUMENTATION =
      SpringWebfluxTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
          .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
          .addAttributesExtractor(
              PeerServiceAttributesExtractor.create(
                  new SpringWebfluxNetAttributesGetter(),
                  CommonConfig.get().getPeerServiceMapping()))
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
