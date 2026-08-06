/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.microsoft.azure.functions.rpc.messages;

/**
 * Stands in for the worker class of the same name, which is generated from
 * https://github.com/Azure/azure-functions-host/blob/dev/src/WebJobs.Script.Grpc/azure-functions-language-worker-protobuf/src/proto/FunctionRpc.proto
 */
public class InvocationResponse {

  public static class Builder {}

  private InvocationResponse() {}
}
