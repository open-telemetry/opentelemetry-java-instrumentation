/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import static reactor.netty.Metrics.OBSERVATION_REGISTRY;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Collections;
import org.springframework.beans.factory.InitializingBean;
import io.opentelemetry.api.trace.Tracer;
import reactor.netty.http.observability.ReactorNettyPropagatingReceiverTracingObservationHandler;
import reactor.netty.http.observability.ReactorNettyPropagatingSenderTracingObservationHandler;
import reactor.netty.observability.ReactorNettyTracingObservationHandler;

final class ReactorNettyHttpInitializingBean implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  ReactorNettyHttpInitializingBean(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    if (openTelemetry == null) {
      return;
    }
    OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();
    Tracer tracer = openTelemetry.getTracer("springboot-reactor-netty");
    OtelTracer otelTracer =
        new OtelTracer(
            tracer,
            otelCurrentTraceContext,
            event -> {},
            new OtelBaggageManager(
                otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));
    Propagator propagator = new OtelPropagator(openTelemetry.getPropagators(), tracer);
    OBSERVATION_REGISTRY
        .observationConfig()
        .observationHandler(new ReactorNettyPropagatingReceiverTracingObservationHandler(otelTracer, propagator))
        .observationHandler(new ReactorNettyPropagatingSenderTracingObservationHandler(otelTracer, propagator))
        .observationHandler(new ReactorNettyTracingObservationHandler(otelTracer));
  }
}
