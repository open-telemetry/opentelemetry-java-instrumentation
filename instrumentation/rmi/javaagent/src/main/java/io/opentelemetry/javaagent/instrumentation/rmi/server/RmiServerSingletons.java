/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class RmiServerSingletons {

  private static final Instrumenter<ClassAndMethod, Void> instrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    RmiServerAttributesGetter rpcAttributesGetter = new RmiServerAttributesGetter();

    InstrumenterBuilder<ClassAndMethod, Void> builder =
        Instrumenter.<ClassAndMethod, Void>builder(
                openTelemetry,
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(openTelemetry, rpcAttributesGetter))
            .addAttributesExtractor(
                RpcServerAttributesExtractor.create(openTelemetry, rpcAttributesGetter));
    setRpcServerExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return instrumenter;
  }

  private RmiServerSingletons() {}
}
