/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import java.util.List;
import reactor.core.publisher.Mono;

final class McpStreamableServerTransportWrapper implements McpStreamableServerTransport {
  private final McpStreamableServerTransport delegate;
  private final McpServerToolCallRequest request;

  McpStreamableServerTransportWrapper(
      McpStreamableServerTransport delegate, McpServerToolCallRequest request) {
    this.delegate = delegate;
    this.request = request;
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    request.captureResponse(message);
    return delegate.sendMessage(message);
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
    request.captureResponse(message);
    return delegate.sendMessage(message, messageId);
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return delegate.unmarshalFrom(data, typeRef);
  }

  @Override
  public List<String> protocolVersions() {
    return delegate.protocolVersions();
  }

  @Override
  public Mono<Void> closeGracefully() {
    return delegate.closeGracefully();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
