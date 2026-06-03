/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class SofaRpcTelemetry {

  private final Instrumenter<SofaRpcRequest, SofaResponse> serverInstrumenter;
  private final Instrumenter<SofaRpcRequest, SofaResponse> clientInstrumenter;

  public static SofaRpcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static SofaRpcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SofaRpcTelemetryBuilder(openTelemetry);
  }

  SofaRpcTelemetry(
      Instrumenter<SofaRpcRequest, SofaResponse> serverInstrumenter,
      Instrumenter<SofaRpcRequest, SofaResponse> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  public Filter newClientFilter() {
    return new TracingFilter(clientInstrumenter, true);
  }

  public Filter newServerFilter() {
    return new TracingFilter(serverInstrumenter, false);
  }
}
