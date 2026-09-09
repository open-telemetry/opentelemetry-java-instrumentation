/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class McpToolCallAttributesExtractorTest {
  private final McpToolCallAttributesExtractor extractor = new McpToolCallAttributesExtractor();

  @Test
  void extractsToolCallAttributesWithoutPayloads() {
    McpToolCallRequest request = new McpToolCallRequest("delete-file");
    request.setRequestId("session-1");
    AttributesBuilder attributes = Attributes.builder();

    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(
        attributes,
        Context.root(),
        request,
        McpSchema.CallToolResult.builder().addTextContent("secret result").build(),
        null);

    assertThat(attributes.build().asMap())
        .containsEntry(stringKey("mcp.method.name"), "tools/call")
        .containsEntry(stringKey("gen_ai.operation.name"), "execute_tool")
        .containsEntry(stringKey("gen_ai.tool.name"), "delete-file")
        .containsEntry(JSONRPC_REQUEST_ID, "session-1")
        .doesNotContainKeys(
            stringKey("gen_ai.tool.call.arguments"), stringKey("gen_ai.tool.call.result"));
  }

  @Test
  void extractsJsonRpcErrorCode() {
    McpToolCallRequest request = new McpToolCallRequest("lookup");
    AttributesBuilder attributes = Attributes.builder();
    McpError error =
        new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(-32601, "Method not found", null));

    extractor.onEnd(attributes, Context.root(), request, null, error);

    assertThat(attributes.build().asMap())
        .containsEntry(ERROR_TYPE, "-32601")
        .containsEntry(RPC_RESPONSE_STATUS_CODE, "-32601");
  }

  @Test
  void extractsToolResultError() {
    McpToolCallRequest request = new McpToolCallRequest("lookup");
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(
        attributes,
        Context.root(),
        request,
        McpSchema.CallToolResult.builder().isError(true).build(),
        null);

    assertThat(attributes.build().get(ERROR_TYPE)).isEqualTo("tool_error");
  }
}
