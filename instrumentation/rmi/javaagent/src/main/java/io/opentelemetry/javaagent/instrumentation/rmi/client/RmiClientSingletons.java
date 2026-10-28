/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.Method;

public class RmiClientSingletons {

  private static final Instrumenter<Method, Void> instrumenter;

  static {
    RmiClientAttributesGetter rpcAttributesGetter = new RmiClientAttributesGetter();
    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .setSchemaUrl("https://opentelemetry.io/schemas/1.44.0")
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addOperationMetrics(RpcClientMetrics.get());
    setRpcClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return instrumenter;
  }

  private RmiClientSingletons() {}
}
