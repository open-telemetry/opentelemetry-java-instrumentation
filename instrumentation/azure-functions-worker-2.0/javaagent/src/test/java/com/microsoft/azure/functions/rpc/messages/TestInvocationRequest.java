/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.microsoft.azure.functions.rpc.messages;

public class TestInvocationRequest extends InvocationRequest {

  private final RpcTraceContext traceContext;

  public TestInvocationRequest(RpcTraceContext traceContext) {
    this.traceContext = traceContext;
  }

  @Override
  public RpcTraceContext getTraceContext() {
    return traceContext;
  }
}
