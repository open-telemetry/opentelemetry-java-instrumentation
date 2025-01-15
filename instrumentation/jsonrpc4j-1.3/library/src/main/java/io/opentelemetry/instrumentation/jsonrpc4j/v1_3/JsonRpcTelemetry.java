/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import com.googlecode.jsonrpc4j.InvocationListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class JsonRpcTelemetry {
  public static JsonRpcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static JsonRpcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JsonRpcTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<JsonRpcServerRequest, JsonRpcServerResponse> serverInstrumenter;
  private final Instrumenter<JsonRpcClientRequest, JsonRpcClientResponse> clientInstrumenter;
  private final ContextPropagators propagators;

  JsonRpcTelemetry(
      Instrumenter<JsonRpcServerRequest, JsonRpcServerResponse> serverInstrumenter,
      Instrumenter<JsonRpcClientRequest, JsonRpcClientResponse> clientInstrumenter,
      ContextPropagators propagators) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
    this.propagators = propagators;
  }

  public InvocationListener newServerInvocationListener() {
    return new OpenTelemetryJsonRpcInvocationListener(serverInstrumenter);
  }

  public Instrumenter<JsonRpcClientRequest, JsonRpcClientResponse> getClientInstrumenter() {
    return clientInstrumenter;
  }

  public ContextPropagators getPropagators() {
    return propagators;
  }
}
