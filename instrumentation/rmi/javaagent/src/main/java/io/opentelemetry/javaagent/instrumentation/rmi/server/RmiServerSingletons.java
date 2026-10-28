/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcMetricsContextCustomizers;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

@SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
public class RmiServerSingletons {

  private static final Instrumenter<RmiServerRequest, Void> instrumenter;

  static {
    RmiServerAttributesGetter rpcAttributesGetter = new RmiServerAttributesGetter();
    RpcSemconvSelection.Selection selection =
        RpcSemconvSelection.Selection.forOpenTelemetry(GlobalOpenTelemetry.get());

    InstrumenterBuilder<RmiServerRequest, Void> builder =
        Instrumenter.<RmiServerRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .setSchemaUrl(selection.schemaUrl())
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter));
    if (selection.emitPreview()) {
      builder
          .addOperationMetrics(RpcServerMetrics.get())
          .addContextCustomizer(
              RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));
    }
    setRpcServerExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<RmiServerRequest, Void> instrumenter() {
    return instrumenter;
  }

  private RmiServerSingletons() {}
}
