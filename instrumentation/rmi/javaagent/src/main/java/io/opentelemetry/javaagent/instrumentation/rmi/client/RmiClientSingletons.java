/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.OpenTelemetryConfigurable;
import java.lang.reflect.Method;

public class RmiClientSingletons {

  private static final Instrumenter<Method, Void> instrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    RmiClientAttributesGetter rpcAttributesGetter = new RmiClientAttributesGetter();
    SpanNameExtractor<Method> spanNameExtractor = RpcSpanNameExtractor.create(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) spanNameExtractor).internalInitialize(openTelemetry);
    AttributesExtractor<Method, Void> rpcAttributesExtractor =
        RpcClientAttributesExtractor.create(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) rpcAttributesExtractor).internalInitialize(openTelemetry);

    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(openTelemetry, "io.opentelemetry.rmi", spanNameExtractor)
            .addAttributesExtractor(rpcAttributesExtractor);
    setRpcClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return instrumenter;
  }

  private RmiClientSingletons() {}
}
