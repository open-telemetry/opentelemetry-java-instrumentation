/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static java.util.Collections.emptyMap;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class McpServerToolCallState {
  private final McpServerToolCallRequest request;

  @Nullable
  public static McpServerToolCallState prepare(
      McpSchema.JSONRPCRequest jsonRpcRequest, @Nullable String sessionId) {
    try {
      if (!McpSchema.METHOD_TOOLS_CALL.equals(jsonRpcRequest.method())) {
        return null;
      }

      Object parameters = jsonRpcRequest.params();
      String toolName = null;
      Map<String, String> meta = emptyMap();
      if (parameters instanceof McpSchema.CallToolRequest callToolRequest) {
        toolName = callToolRequest.name();
        meta = copyMeta(callToolRequest.meta());
      } else if (parameters instanceof Map<?, ?> parameterMap) {
        Object rawToolName = parameterMap.get("name");
        toolName = rawToolName instanceof String string ? string : null;
        Object rawMeta = parameterMap.get("_meta");
        if (rawMeta instanceof Map<?, ?> metaMap) {
          meta = copyMeta(metaMap);
        }
      }

      Object requestId = jsonRpcRequest.id();
      return new McpServerToolCallState(
          new McpServerToolCallRequest(
              toolName, requestId == null ? null : requestId.toString(), sessionId, meta));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Map<String, String> copyMeta(@Nullable Map<?, ?> source) {
    if (source == null || source.isEmpty()) {
      return emptyMap();
    }
    Map<String, String> copy = new HashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
        copy.put(key, value);
      }
    }
    return copy;
  }

  private McpServerToolCallState(McpServerToolCallRequest request) {
    this.request = request;
  }

  public McpStreamableServerTransport wrap(McpStreamableServerTransport transport) {
    return new McpStreamableServerTransportWrapper(transport, request);
  }

  @Nullable
  public Mono<?> finish(@Nullable Mono<?> publisher) {
    return publisher == null
        ? null
        : McpServerToolCallMono.wrap(publisher.doOnNext(request::captureResponse), request);
  }
}
