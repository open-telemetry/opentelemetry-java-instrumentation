/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcMetricsContextCustomizers;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.Method;

@SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
public class RmiClientSingletons {

  private static final Instrumenter<Method, Void> instrumenter;

  static {
    RmiClientAttributesGetter rpcAttributesGetter = new RmiClientAttributesGetter();
    RpcSemconvSelection.Selection selection =
        RpcSemconvSelection.Selection.forOpenTelemetry(GlobalOpenTelemetry.get());

    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .setSchemaUrl(selection.schemaUrl())
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter));
    if (selection.emitPreview()) {
      builder
          .addOperationMetrics(RpcClientMetrics.get())
          .addContextCustomizer(
              RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));
    }
    setRpcClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return instrumenter;
  }

  private RmiClientSingletons() {}
}
