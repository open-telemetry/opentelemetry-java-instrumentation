/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public final class WebfluxSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.0";

  private static final Instrumenter<Object, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<Object, Void> builder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, new WebfluxSpanNameExtractor());

    INSTRUMENTER =
        builder
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .addAttributesExtractor(
                CodeAttributesExtractor.create(new HandlerCodeAttributesGetter()))
            .buildInstrumenter();
  }

  public static Instrumenter<Object, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpServerRouteGetter<ServerWebExchange> httpRouteGetter() {
    return (context, exchange) -> {
      Object bestPatternObj = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (bestPatternObj == null) {
        return null;
      }
      if (bestPatternObj instanceof PathPattern) {
        return ((PathPattern) bestPatternObj).getPatternString();
      }
      return bestPatternObj.toString();
    };
  }

  private WebfluxSingletons() {}
}
