/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.shenyu;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public class ShenYuSingletons {

  private static final String INSTRUMENTATION_NAME = "org.apache.shenyu";

  private static final Instrumenter<Object, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<Object, Void> builder =
        Instrumenter.builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, new ShenYuSpanNameExtractor());

    INSTRUMENTER = builder.newInstrumenter();
  }

  public static Instrumenter<Object, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpRouteGetter<ServerWebExchange> httpRouteGetter() {
    return (context, exchange) -> {
      PathPattern bestPattern =
          exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      return bestPattern == null ? null : bestPattern.getPatternString();
    };
  }

  private ShenYuSingletons() {}
}
