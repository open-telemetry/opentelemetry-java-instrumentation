/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

class TestMcpServerTransport implements McpServerTransport {
  private final AtomicReference<McpSchema.JSONRPCMessage> sentMessage = new AtomicReference<>();

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    sentMessage.set(message);
    return Mono.empty();
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

  McpSchema.JSONRPCResponse getResponse() {
    return (McpSchema.JSONRPCResponse) sentMessage.get();
  }

  void clear() {
    sentMessage.set(null);
  }

  static final class Streamable extends TestMcpServerTransport
      implements McpStreamableServerTransport {
    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
      return sendMessage(message);
    }
  }

  static final class Stateless implements McpStatelessServerTransport {
    private McpStatelessServerHandler handler;

    @Override
    public void setMcpHandler(McpStatelessServerHandler handler) {
      this.handler = handler;
    }

    @Override
    public Mono<Void> closeGracefully() {
      return Mono.empty();
    }

    McpStatelessServerHandler getHandler() {
      return handler;
    }
  }
}
