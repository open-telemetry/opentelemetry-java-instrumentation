/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.client.ClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server.ServerAttributesGetter;
import java.lang.reflect.Method;

public final class SpringRmiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rmi-4.0";

  private static final Instrumenter<Method, Void> CLIENT_INSTRUMENTER = buildClientInstrumenter();
  private static final Instrumenter<ClassAndMethod, Void> SERVER_INSTRUMENTER =
      buildServerInstrumenter();

  private static Instrumenter<Method, Void> buildClientInstrumenter() {
    ClientAttributesGetter rpcAttributesGetter = ClientAttributesGetter.INSTANCE;

    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter));
    Experimental.setExceptionEventExtractor(builder, RpcExceptionEventExtractors.client());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static Instrumenter<ClassAndMethod, Void> buildServerInstrumenter() {
    ServerAttributesGetter rpcAttributesGetter = ServerAttributesGetter.INSTANCE;

    InstrumenterBuilder<ClassAndMethod, Void> builder =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter));
    Experimental.setExceptionEventExtractor(builder, RpcExceptionEventExtractors.server());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> clientInstrumenter() {
    return CLIENT_INSTRUMENTER;
  }

  public static Instrumenter<ClassAndMethod, Void> serverInstrumenter() {
    return SERVER_INSTRUMENTER;
  }

  private SpringRmiSingletons() {}
}
