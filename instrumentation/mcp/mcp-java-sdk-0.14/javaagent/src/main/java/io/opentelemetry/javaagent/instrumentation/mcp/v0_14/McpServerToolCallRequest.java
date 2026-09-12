/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import java.util.Map;
import javax.annotation.Nullable;

final class McpServerToolCallRequest {
  private static final int PARSE_ERROR = -32700;
  private static final int INVALID_REQUEST = -32600;
  private static final int METHOD_NOT_FOUND = -32601;
  private static final int INVALID_PARAMS = -32602;
  private static final int RESOURCE_NOT_FOUND = -32002;

  @Nullable private final String toolName;
  @Nullable private final String requestId;
  @Nullable private final String sessionId;
  private final Map<String, String> meta;

  private Context ambientContext = Context.root();
  @Nullable private volatile McpSchema.JSONRPCResponse response;

  McpServerToolCallRequest(
      @Nullable String toolName,
      @Nullable String requestId,
      @Nullable String sessionId,
      Map<String, String> meta) {
    this.toolName = toolName;
    this.requestId = requestId;
    this.sessionId = sessionId;
    this.meta = meta;
  }

  @Nullable
  String getToolName() {
    return toolName;
  }

  @Nullable
  String getRequestId() {
    return requestId;
  }

  @Nullable
  String getSessionId() {
    return sessionId;
  }

  Map<String, String> getMeta() {
    return meta;
  }

  Context getAmbientContext() {
    return ambientContext;
  }

  void setAmbientContext(Context ambientContext) {
    this.ambientContext = ambientContext;
  }

  void captureResponse(Object result) {
    if (result instanceof McpSchema.JSONRPCResponse jsonRpcResponse) {
      response = jsonRpcResponse;
    }
  }

  boolean isToolError() {
    McpSchema.JSONRPCResponse jsonRpcResponse = response;
    return jsonRpcResponse != null
        && jsonRpcResponse.result() instanceof McpSchema.CallToolResult callToolResult
        && Boolean.TRUE.equals(callToolResult.isError());
  }

  boolean isServerJsonRpcError() {
    McpSchema.JSONRPCResponse.JSONRPCError error = getResponseError();
    if (error == null || error.code() == null) {
      return false;
    }
    int code = error.code();
    return code != PARSE_ERROR
        && code != INVALID_REQUEST
        && code != METHOD_NOT_FOUND
        && code != INVALID_PARAMS
        && code != RESOURCE_NOT_FOUND;
  }

  @Nullable
  String getResponseErrorCode() {
    McpSchema.JSONRPCResponse.JSONRPCError error = getResponseError();
    return error == null || error.code() == null ? null : error.code().toString();
  }

  @Nullable
  String getResponseErrorMessage() {
    McpSchema.JSONRPCResponse.JSONRPCError error = getResponseError();
    return error == null ? null : error.message();
  }

  @Nullable
  private McpSchema.JSONRPCResponse.JSONRPCError getResponseError() {
    McpSchema.JSONRPCResponse jsonRpcResponse = response;
    return jsonRpcResponse == null ? null : jsonRpcResponse.error();
  }
}
