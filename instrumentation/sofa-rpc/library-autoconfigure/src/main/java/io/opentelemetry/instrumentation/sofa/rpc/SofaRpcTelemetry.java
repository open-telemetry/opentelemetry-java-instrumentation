/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting SOFARPC servers and clients. */
public final class SofaRpcTelemetry {

  private final Instrumenter<SofaRpcRequest, SofaResponse> serverInstrumenter;
  private final Instrumenter<SofaRpcRequest, SofaResponse> clientInstrumenter;

  /** Returns a new {@link SofaRpcTelemetry} configured with the given {@link OpenTelemetry}. */
  public static SofaRpcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SofaRpcTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static SofaRpcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SofaRpcTelemetryBuilder(openTelemetry);
  }

  SofaRpcTelemetry(
      Instrumenter<SofaRpcRequest, SofaResponse> serverInstrumenter,
      Instrumenter<SofaRpcRequest, SofaResponse> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  /** Returns a new SOFARPC client {@link Filter} that traces SOFARPC RPC invocations. */
  public Filter newClientFilter() {
    return new TracingFilter(clientInstrumenter, true);
  }

  /** Returns a new SOFARPC server {@link Filter} that traces SOFARPC RPC invocations. */
  public Filter newServerFilter() {
    return new TracingFilter(serverInstrumenter, false);
  }
}
