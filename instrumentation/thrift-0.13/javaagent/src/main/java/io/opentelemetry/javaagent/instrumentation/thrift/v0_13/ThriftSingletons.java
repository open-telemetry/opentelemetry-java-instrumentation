/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ThriftInstrumenterFactory;
import java.util.function.UnaryOperator;

public final class ThriftSingletons {

  private static final Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter =
      ThriftInstrumenterFactory.createClientInstrumenter(
          GlobalOpenTelemetry.get(), UnaryOperator.identity(), emptyList(), emptyList());
  private static final Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter =
      ThriftInstrumenterFactory.createServerInstrumenter(
          GlobalOpenTelemetry.get(), UnaryOperator.identity(), emptyList(), emptyList());

  public static Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter() {
    return clientInstrumenter;
  }

  public static Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter() {
    return serverInstrumenter;
  }

  public static ContextPropagators getPropagators() {
    return GlobalOpenTelemetry.get().getPropagators();
  }

  private ThriftSingletons() {}
}
