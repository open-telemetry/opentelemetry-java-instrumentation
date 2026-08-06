/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.microsoft.azure.functions.rpc.messages;

/**
 * Stub for the protobuf generated class, see
 * https://github.com/Azure/azure-functions-host/blob/dev/src/WebJobs.Script.Grpc/azure-functions-language-worker-protobuf/src/proto/FunctionRpc.proto
 */
public class RpcTraceContext {

  public String getTraceParent() {
    throw new UnsupportedOperationException();
  }

  public String getTraceState() {
    throw new UnsupportedOperationException();
  }
}
