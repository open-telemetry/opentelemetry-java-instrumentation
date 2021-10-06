/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Result;

/** Entrypoint for tracing Apache Dubbo servers and clients. */
public final class DubboTracing {

  /** Returns a new {@link DubboTracing} configured with the given {@link OpenTelemetry}. */
  public static DubboTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /** Returns a new {@link DubboTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static DubboTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new DubboTracingBuilder(openTelemetry);
  }

  private final Instrumenter<DubboRequest, Result> serverInstrumenter;
  private final Instrumenter<DubboRequest, Result> clientInstrumenter;

  DubboTracing(
      Instrumenter<DubboRequest, Result> serverInstrumenter,
      Instrumenter<DubboRequest, Result> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  /** Returns a new Dubbo {@link Filter} that traces Dubbo RPC invocations. */
  public Filter newFilter() {
    return new TracingFilter(serverInstrumenter, clientInstrumenter);
  }
}
