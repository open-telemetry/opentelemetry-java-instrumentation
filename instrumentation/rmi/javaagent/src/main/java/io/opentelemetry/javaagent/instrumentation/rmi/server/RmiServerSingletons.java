/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class RmiServerSingletons {

  private static final Instrumenter<ClassAndMethod, Void> instrumenter;

  static {
    RmiServerAttributesGetter rpcAttributesGetter = new RmiServerAttributesGetter();

    InstrumenterBuilder<ClassAndMethod, Void> builder =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter));
    setRpcServerExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return instrumenter;
  }

  private RmiServerSingletons() {}
}
