/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

public final class SimpleJsonRpcResponse {

  private final Object result;

  public SimpleJsonRpcResponse(Object result) {
    this.result = result;
  }

  public Object getResult() {
    return result;
  }
}
