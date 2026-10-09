/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.client.ClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server.ServerAttributesGetter;
import java.lang.reflect.Method;

public class SpringRmiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rmi-4.0";

  private static final Instrumenter<Method, Void> clientInstrumenter = buildClientInstrumenter();
  private static final Instrumenter<ClassAndMethod, Void> serverInstrumenter =
      buildServerInstrumenter();

  private static Instrumenter<Method, Void> buildClientInstrumenter() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    ClientAttributesGetter rpcAttributesGetter = new ClientAttributesGetter();

    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(openTelemetry, rpcAttributesGetter))
            .addAttributesExtractor(
                RpcClientAttributesExtractor.create(openTelemetry, rpcAttributesGetter));
    setRpcClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static Instrumenter<ClassAndMethod, Void> buildServerInstrumenter() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    ServerAttributesGetter rpcAttributesGetter = new ServerAttributesGetter();

    InstrumenterBuilder<ClassAndMethod, Void> builder =
        Instrumenter.<ClassAndMethod, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(openTelemetry, rpcAttributesGetter))
            .addAttributesExtractor(
                RpcServerAttributesExtractor.create(openTelemetry, rpcAttributesGetter));
    setRpcServerExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> clientInstrumenter() {
    return clientInstrumenter;
  }

  public static Instrumenter<ClassAndMethod, Void> serverInstrumenter() {
    return serverInstrumenter;
  }

  private SpringRmiSingletons() {}
}
