/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import static reactor.netty.Metrics.OBSERVATION_REGISTRY;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Collections;
import org.springframework.beans.factory.InitializingBean;
import reactor.netty.observability.ReactorNettyTracingObservationHandler;

final class ReactorNettyHttpClientInitializingBean implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  ReactorNettyHttpClientInitializingBean(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    if (openTelemetry == null) {
      return;
    }
    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
    OtelTracer otelTracer =
        new OtelTracer(
            openTelemetry.getTracer("springboot-reactor-netty"),
            otelCurrentTraceContext,
            event -> {},
            new OtelBaggageManager(
                otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
    OBSERVATION_REGISTRY
        .observationConfig()
        .observationHandler(new ReactorNettyTracingObservationHandler(otelTracer));
  }
}
