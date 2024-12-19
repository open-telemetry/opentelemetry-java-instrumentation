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

  /** Returns a new client {@link DubboTelemetry} configured with the given {@link OpenTelemetry}. */
  public static DubboTelemetry createClient(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).buildClient();
  }

  /** Returns a new server {@link DubboTelemetry} configured with the given {@link OpenTelemetry}. */
  public static DubboTelemetry createServer(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).buildServer();
  }

  /**
   * Returns a new {@link DubboTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static DubboTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new DubboTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<DubboRequest, Result> instrumenter;

  private final boolean isClient;

  DubboTelemetry(Instrumenter<DubboRequest, Result> instrumenter, boolean isClient) {
    this.instrumenter = instrumenter;
    this.isClient = isClient;
  }

  /** Returns a new Dubbo {@link Filter} that traces Dubbo RPC invocations. */
  public Filter newFilter() {
    return new TracingFilter(instrumenter, isClient);
  }
}
