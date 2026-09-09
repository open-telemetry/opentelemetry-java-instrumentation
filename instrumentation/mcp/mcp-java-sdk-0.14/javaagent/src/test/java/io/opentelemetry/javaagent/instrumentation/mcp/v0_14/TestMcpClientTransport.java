/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import reactor.core.publisher.Mono;

final class TestMcpClientTransport implements McpClientTransport {
  enum ToolResponse {
    SUCCESS,
    TOOL_ERROR,
    JSON_RPC_ERROR,
    NONE
  }

  private static final McpSchema.InitializeResult INITIALIZE_RESULT =
      new McpSchema.InitializeResult(
          ProtocolVersions.MCP_2024_11_05,
          McpSchema.ServerCapabilities.builder().tools(false).build(),
          new McpSchema.Implementation("test-server", "1.0"),
          null);

  private final ToolResponse toolResponse;
  private final AtomicReference<McpSchema.JSONRPCRequest> toolRequest = new AtomicReference<>();
  private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler;

  TestMcpClientTransport(ToolResponse toolResponse) {
    this.toolResponse = toolResponse;
  }

  @Override
  public Mono<Void> connect(
      Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    this.handler = handler;
    return Mono.empty();
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.empty();
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    if (!(message instanceof McpSchema.JSONRPCRequest request)) {
      return Mono.empty();
    }

    if (McpSchema.METHOD_INITIALIZE.equals(request.method())) {
      return respond(successResponse(request.id(), INITIALIZE_RESULT));
    }
    if (!McpSchema.METHOD_TOOLS_CALL.equals(request.method())) {
      return respond(successResponse(request.id(), new Object()));
    }

    toolRequest.set(request);
    return switch (toolResponse) {
      case SUCCESS ->
          respond(
              successResponse(
                  request.id(),
                  McpSchema.CallToolResult.builder().addTextContent("secret").build()));
      case TOOL_ERROR ->
          respond(
              successResponse(
                  request.id(), McpSchema.CallToolResult.builder().isError(true).build()));
      case JSON_RPC_ERROR ->
          respond(
              new McpSchema.JSONRPCResponse(
                  McpSchema.JSONRPC_VERSION,
                  request.id(),
                  null,
                  new McpSchema.JSONRPCResponse.JSONRPCError(-32601, "Method not found", null)));
      case NONE -> Mono.empty();
    };
  }

  private static McpSchema.JSONRPCResponse successResponse(Object id, Object result) {
    return new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, id, result, null);
  }

  private Mono<Void> respond(McpSchema.JSONRPCResponse response) {
    return handler.apply(Mono.just(response)).then();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return (T) data;
  }

  McpSchema.JSONRPCRequest getToolRequest() {
    return toolRequest.get();
  }
}
