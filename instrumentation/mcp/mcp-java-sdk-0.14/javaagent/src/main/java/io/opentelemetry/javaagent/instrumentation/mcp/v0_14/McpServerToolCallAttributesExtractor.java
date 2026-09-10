/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_SESSION_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class McpServerToolCallAttributesExtractor
    implements AttributesExtractor<McpServerToolCallRequest, Void> {

  @Override
  @SuppressWarnings("deprecation") // using deprecated semconv
  public void onStart(
      AttributesBuilder attributes, Context parentContext, McpServerToolCallRequest request) {
    attributes.put(MCP_METHOD_NAME, McpSchema.METHOD_TOOLS_CALL);
    attributes.put(GEN_AI_OPERATION_NAME, "execute_tool");
    attributes.put(GEN_AI_TOOL_NAME, request.getToolName());
    attributes.put(JSONRPC_REQUEST_ID, request.getRequestId());
    attributes.put(MCP_SESSION_ID, request.getSessionId());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      McpServerToolCallRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {
    String responseErrorCode = request.getResponseErrorCode();
    attributes.put(RPC_RESPONSE_STATUS_CODE, responseErrorCode);

    if (error != null) {
      attributes.put(ERROR_TYPE, error.getClass().getName());
    } else if (request.isToolError()) {
      attributes.put(ERROR_TYPE, "tool_error");
    } else if (request.isServerJsonRpcError()) {
      attributes.put(ERROR_TYPE, responseErrorCode);
    }
  }
}
