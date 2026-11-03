/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14.McpSingletons.currentState;
import static io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14.McpSingletons.propagators;
import static java.util.logging.Level.FINE;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class McpToolCallState {
  private static final Logger logger = Logger.getLogger(McpToolCallState.class.getName());

  private final McpClientSession session;
  private final String toolName;
  private final McpSchema.CallToolRequest requestParameters;
  private final Map<String, Object> propagationMeta;
  @Nullable private volatile String requestId;

  @Nullable
  public static McpToolCallState prepare(
      McpClientSession session, String method, @Nullable Object requestParameters) {
    try {
      if (!McpSchema.METHOD_TOOLS_CALL.equals(method)
          || !(requestParameters instanceof McpSchema.CallToolRequest callToolRequest)) {
        return null;
      }

      McpToolCallState current = currentState().get();
      if (current != null
          && current.session == session
          && current.requestParameters == requestParameters) {
        return null;
      }

      Map<String, Object> meta =
          callToolRequest.meta() == null ? new HashMap<>() : new HashMap<>(callToolRequest.meta());
      McpToolCallState state =
          new McpToolCallState(
              session,
              callToolRequest.name(),
              new McpSchema.CallToolRequest(
                  callToolRequest.name(), callToolRequest.arguments(), meta),
              meta);
      return state;
    } catch (Throwable t) {
      logger.log(FINE, "Failed to prepare MCP tool call instrumentation", t);
      return null;
    }
  }

  private McpToolCallState(
      McpClientSession session,
      String toolName,
      McpSchema.CallToolRequest requestParameters,
      Map<String, Object> propagationMeta) {
    this.session = session;
    this.toolName = toolName;
    this.requestParameters = requestParameters;
    this.propagationMeta = propagationMeta;
  }

  void captureRequestId(String requestId) {
    this.requestId = requestId;
  }

  McpToolCallState copy() {
    Map<String, Object> meta = new HashMap<>(propagationMeta);
    return new McpToolCallState(
        session,
        toolName,
        new McpSchema.CallToolRequest(
            requestParameters.name(), requestParameters.arguments(), meta),
        meta);
  }

  Mono<?> sendRequest(TypeRef<?> responseType) {
    // Bypass the deferred wrapper while the SDK creates this subscription's request and ID.
    McpToolCallState previous = currentState().set(this);
    try {
      return session.sendRequest(McpSchema.METHOD_TOOLS_CALL, requestParameters, responseType);
    } finally {
      currentState().restore(previous);
    }
  }

  McpToolCallRequest createRequest() {
    McpToolCallRequest request =
        new McpToolCallRequest(toolName, McpProtocolVersionState.get(session));
    if (requestId != null) {
      request.setRequestId(requestId);
    }
    return request;
  }

  void inject(Context context) {
    propagators()
        .getTextMapPropagator()
        .inject(context, propagationMeta, (carrier, key, value) -> carrier.put(key, value));
  }

  public Mono<?> wrap(TypeRef<?> responseType) {
    return McpToolCallMono.wrap(this, responseType);
  }
}
