/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.microsoft.azure.functions.rpc.messages;

public class TestRpcTraceContext extends RpcTraceContext {

  private final String traceParent;
  private final String traceState;

  public TestRpcTraceContext(String traceParent, String traceState) {
    this.traceParent = traceParent;
    this.traceState = traceState;
  }

  @Override
  public String getTraceParent() {
    return traceParent;
  }

  @Override
  public String getTraceState() {
    return traceState;
  }
}
