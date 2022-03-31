/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springrmi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.instrumentation.springrmi.client.ClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.springrmi.server.ServerAttributesGetter;
import java.lang.reflect.Method;

public final class SpringRmiSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rmi-4.0";

  private static final Instrumenter<Method, Void> CLIENT_INSTRUMENTER = buildClientInstrumenter();
  private static final Instrumenter<ClassAndMethod, Void> SERVER_INSTRUMENTER =
      buildServerInstrumenter();

  private static Instrumenter<Method, Void> buildClientInstrumenter() {
    ClientAttributesGetter rpcAttributesGetter = ClientAttributesGetter.INSTANCE;

    return Instrumenter.<Method, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            RpcSpanNameExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static Instrumenter<ClassAndMethod, Void> buildServerInstrumenter() {
    ServerAttributesGetter rpcAttributesGetter = ServerAttributesGetter.INSTANCE;

    return Instrumenter.<ClassAndMethod, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            RpcSpanNameExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
        .newInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> clientInstrumenter() {
    return CLIENT_INSTRUMENTER;
  }

  public static Instrumenter<ClassAndMethod, Void> serverInstrumenter() {
    return SERVER_INSTRUMENTER;
  }

  private SpringRmiSingletons() {}
}
