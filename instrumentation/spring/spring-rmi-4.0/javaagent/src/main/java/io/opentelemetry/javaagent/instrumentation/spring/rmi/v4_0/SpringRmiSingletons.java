/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcMetricsContextCustomizers;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.client.ClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server.ServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server.SpringRmiServerRequest;
import java.lang.reflect.Method;

public class SpringRmiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rmi-4.0";

  private static final Instrumenter<Method, Void> clientInstrumenter = buildClientInstrumenter();
  private static final Instrumenter<SpringRmiServerRequest, Void> serverInstrumenter =
      buildServerInstrumenter();

  @SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
  private static Instrumenter<Method, Void> buildClientInstrumenter() {
    ClientAttributesGetter rpcAttributesGetter = new ClientAttributesGetter();

    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .setSchemaUrl(
                RpcSemconvSelection.Selection.forOpenTelemetry(GlobalOpenTelemetry.get())
                    .schemaUrl())
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter));
    if (RpcSemconvSelection.Selection.forOpenTelemetry(GlobalOpenTelemetry.get()).emitPreview()) {
      builder
          .addOperationMetrics(RpcClientMetrics.get())
          .addContextCustomizer(
              RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));
    }
    setRpcClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  @SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
  private static Instrumenter<SpringRmiServerRequest, Void> buildServerInstrumenter() {
    ServerAttributesGetter rpcAttributesGetter = new ServerAttributesGetter();

    InstrumenterBuilder<SpringRmiServerRequest, Void> builder =
        Instrumenter.<SpringRmiServerRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .setSchemaUrl(
                RpcSemconvSelection.Selection.forOpenTelemetry(GlobalOpenTelemetry.get())
                    .schemaUrl())
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter));
    if (RpcSemconvSelection.Selection.forOpenTelemetry(GlobalOpenTelemetry.get()).emitPreview()) {
      builder
          .addOperationMetrics(RpcServerMetrics.get())
          .addContextCustomizer(
              RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));
    }
    setRpcServerExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> clientInstrumenter() {
    return clientInstrumenter;
  }

  public static Instrumenter<SpringRmiServerRequest, Void> serverInstrumenter() {
    return serverInstrumenter;
  }

  private SpringRmiSingletons() {}
}
