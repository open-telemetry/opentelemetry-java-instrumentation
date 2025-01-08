/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_6;

import com.googlecode.jsonrpc4j.InvocationListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.JsonRpcTelemetry;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.SimpleJsonRpcRequest;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_6.SimpleJsonRpcResponse;

public final class JsonRpcSingletons {

  public static final InvocationListener SERVER_INVOCATION_LISTENER;

  public static final Instrumenter<SimpleJsonRpcRequest, SimpleJsonRpcResponse> CLIENT_INSTRUMENTER;

  static {
    JsonRpcTelemetry telemetry = JsonRpcTelemetry.builder(GlobalOpenTelemetry.get()).build();

    SERVER_INVOCATION_LISTENER = telemetry.newServerInvocationListener();
    CLIENT_INSTRUMENTER = telemetry.getClientInstrumenter();
  }

  private JsonRpcSingletons() {}
}
