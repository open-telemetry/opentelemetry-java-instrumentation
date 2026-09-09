/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class McpToolCallState {
  private static final ThreadLocal<McpToolCallState> currentState = new ThreadLocal<>();

  private final McpToolCallRequest request;
  private final McpSchema.CallToolRequest requestParameters;
  private final Map<String, Object> propagationMeta;
  @Nullable private final McpToolCallState previousState;

  @Nullable
  public static McpToolCallState prepare(String method, Object requestParameters) {
    try {
      if (!McpSchema.METHOD_TOOLS_CALL.equals(method)
          || !(requestParameters instanceof McpSchema.CallToolRequest callToolRequest)) {
        return null;
      }

      Map<String, Object> meta =
          callToolRequest.meta() == null ? new HashMap<>() : new HashMap<>(callToolRequest.meta());
      McpToolCallState state =
          new McpToolCallState(
              new McpToolCallRequest(callToolRequest.name()),
              new McpSchema.CallToolRequest(
                  callToolRequest.name(), callToolRequest.arguments(), meta),
              meta,
              currentState.get());
      currentState.set(state);
      return state;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static void captureRequestId(String requestId) {
    McpToolCallState state = currentState.get();
    if (state != null) {
      state.request.setRequestId(requestId);
    }
  }

  private McpToolCallState(
      McpToolCallRequest request,
      McpSchema.CallToolRequest requestParameters,
      Map<String, Object> propagationMeta,
      @Nullable McpToolCallState previousState) {
    this.request = request;
    this.requestParameters = requestParameters;
    this.propagationMeta = propagationMeta;
    this.previousState = previousState;
  }

  public Object getRequestParameters() {
    return requestParameters;
  }

  McpToolCallRequest getRequest() {
    return request;
  }

  void inject(Context context) {
    try {
      McpSingletons.propagators()
          .getTextMapPropagator()
          .inject(context, propagationMeta, (carrier, key, value) -> carrier.put(key, value));
    } catch (Throwable ignored) {
      // Preserve the original MCP request if a configured propagator fails.
    }
  }

  @Nullable
  public Mono<?> finish(@Nullable Mono<?> publisher) {
    if (previousState == null) {
      currentState.remove();
    } else {
      currentState.set(previousState);
    }
    return publisher == null ? null : McpToolCallMono.wrap(publisher, this);
  }
}
