/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class McpToolCallAttributesExtractor
    implements AttributesExtractor<McpToolCallRequest, McpSchema.CallToolResult> {
  private static final AttributeKey<String> MCP_METHOD_NAME = stringKey("mcp.method.name");
  private static final AttributeKey<String> GEN_AI_OPERATION_NAME =
      stringKey("gen_ai.operation.name");
  private static final AttributeKey<String> GEN_AI_TOOL_NAME = stringKey("gen_ai.tool.name");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, McpToolCallRequest request) {
    attributes.put(MCP_METHOD_NAME, McpSchema.METHOD_TOOLS_CALL);
    attributes.put(GEN_AI_OPERATION_NAME, "execute_tool");
    attributes.put(GEN_AI_TOOL_NAME, request.getToolName());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      McpToolCallRequest request,
      @Nullable McpSchema.CallToolResult response,
      @Nullable Throwable error) {
    if (request.getRequestId() != null) {
      attributes.put(JSONRPC_REQUEST_ID, request.getRequestId());
    }

    if (response != null && Boolean.TRUE.equals(response.isError())) {
      attributes.put(ERROR_TYPE, "tool_error");
      return;
    }

    if (error instanceof McpError mcpError) {
      McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError = mcpError.getJsonRpcError();
      if (jsonRpcError != null) {
        String code = jsonRpcError.code().toString();
        attributes.put(ERROR_TYPE, code);
        attributes.put(RPC_RESPONSE_STATUS_CODE, code);
        return;
      }
    }

    if (error != null) {
      attributes.put(ERROR_TYPE, error.getClass().getName());
    }
  }
}
