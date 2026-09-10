/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import java.util.function.Function;
import reactor.core.publisher.Mono;

final class TestMcpClientServerTransport implements McpClientTransport, McpServerTransport {
  private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> clientHandler;
  private McpServerSession serverSession;

  void setServerSession(McpServerSession serverSession) {
    this.serverSession = serverSession;
  }

  @Override
  public Mono<Void> connect(
      Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    clientHandler = handler;
    return Mono.empty();
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    if (message instanceof McpSchema.JSONRPCResponse) {
      return clientHandler.apply(Mono.just(message)).then();
    }
    return serverSession.handle(message);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return (T) data;
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.empty();
  }
}
