/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_SESSION_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerToolCallAttributesExtractorTest {
  private final McpServerToolCallAttributesExtractor extractor =
      new McpServerToolCallAttributesExtractor();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void extractsServerAttributesWithoutPayloads() {
    McpServerToolCallRequest request =
        new McpServerToolCallRequest(
            "delete-file", "request-7", "session-1", Map.of("traceparent", "secret"));
    request.captureResponse(
        new McpSchema.JSONRPCResponse(
            McpSchema.JSONRPC_VERSION,
            "request-7",
            McpSchema.CallToolResult.builder().addTextContent("secret result").build(),
            null));
    AttributesBuilder attributes = Attributes.builder();

    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().asMap())
        .containsEntry(MCP_METHOD_NAME, "tools/call")
        .containsEntry(GEN_AI_OPERATION_NAME, "execute_tool")
        .containsEntry(GEN_AI_TOOL_NAME, "delete-file")
        .containsEntry(JSONRPC_REQUEST_ID, "request-7")
        .containsEntry(MCP_SESSION_ID, "session-1")
        .doesNotContainKeys(
            stringKey("gen_ai.tool.call.arguments"), stringKey("gen_ai.tool.call.result"));
  }

  @Test
  void extractsToolResultError() {
    McpServerToolCallRequest request = request();
    request.captureResponse(
        new McpSchema.JSONRPCResponse(
            McpSchema.JSONRPC_VERSION,
            "1",
            McpSchema.CallToolResult.builder().isError(true).build(),
            null));
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().asMap()).containsEntry(ERROR_TYPE, "tool_error");
    assertThat(request.isToolError()).isTrue();
  }

  @Test
  void doesNotClassifyCallerJsonRpcErrorAsServerError() {
    McpServerToolCallRequest request = request();
    request.captureResponse(errorResponse(-32602, "Invalid params"));
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().asMap())
        .containsEntry(RPC_RESPONSE_STATUS_CODE, "-32602")
        .doesNotContainKey(ERROR_TYPE);
    assertThat(request.isServerJsonRpcError()).isFalse();
  }

  @Test
  void classifiesInternalJsonRpcErrorAsServerError() {
    McpServerToolCallRequest request = request();
    request.captureResponse(errorResponse(-32603, "Internal error"));
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().asMap())
        .containsEntry(RPC_RESPONSE_STATUS_CODE, "-32603")
        .containsEntry(ERROR_TYPE, "-32603");
    assertThat(request.isServerJsonRpcError()).isTrue();
  }

  private static McpServerToolCallRequest request() {
    return new McpServerToolCallRequest("lookup", "1", null, Map.of());
  }

  private static McpSchema.JSONRPCResponse errorResponse(int code, String message) {
    return new McpSchema.JSONRPCResponse(
        McpSchema.JSONRPC_VERSION,
        "1",
        null,
        new McpSchema.JSONRPCResponse.JSONRPCError(code, message, null));
  }
}
