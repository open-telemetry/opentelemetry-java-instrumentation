/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Result;

/** Entrypoint for instrumenting Apache Dubbo servers and clients. */
public final class DubboTelemetry {

  private final Instrumenter<DubboRequest, Result> serverInstrumenter;
  private final Instrumenter<DubboRequest, Result> clientInstrumenter;

  /** Returns a new {@link DubboTelemetry} configured with the given {@link OpenTelemetry}. */
  public static DubboTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link DubboTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static DubboTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new DubboTelemetryBuilder(openTelemetry);
  }

  DubboTelemetry(
      Instrumenter<DubboRequest, Result> serverInstrumenter,
      Instrumenter<DubboRequest, Result> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  /** Returns a new Dubbo client {@link Filter} that traces Dubbo RPC invocations. */
  public Filter newClientFilter() {
    return new TracingFilter(clientInstrumenter, true);
  }

  /** Returns a new Dubbo server {@link Filter} that traces Dubbo RPC invocations. */
  public Filter newServerFilter() {
    return new TracingFilter(serverInstrumenter, false);
  }
}
