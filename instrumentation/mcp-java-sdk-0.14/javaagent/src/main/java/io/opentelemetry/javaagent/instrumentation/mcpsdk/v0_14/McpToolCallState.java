/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14.McpSingletons.propagators;
import static java.util.logging.Level.FINE;

import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class McpToolCallState {
  private static final ContextKey<McpToolCallState> STATE_KEY = ContextKey.named("mcp-tool-call");
  private static final Logger logger = Logger.getLogger(McpToolCallState.class.getName());

  private final McpSchema.CallToolRequest requestParameters;
  private final McpToolCallRequest request;
  private final Context callerContext;

  McpToolCallState(
      McpClientSession session,
      McpSchema.CallToolRequest requestParameters,
      Context callerContext) {
    this.requestParameters = requestParameters;
    this.callerContext = callerContext;
    request =
        new McpToolCallRequest(requestParameters.name(), McpProtocolVersionState.get(session));
  }

  McpToolCallRequest getRequest() {
    return request;
  }

  Context storeInContext(Context context) {
    return context.with(STATE_KEY, this);
  }

  @Nullable
  static Context getCallerContext(Context context) {
    McpToolCallState state = context.get(STATE_KEY);
    return state == null ? null : state.callerContext;
  }

  public static McpSchema.JSONRPCMessage inject(McpSchema.JSONRPCMessage message) {
    Context context = Context.current();
    McpToolCallState state = context.get(STATE_KEY);
    if (state == null
        || !(message instanceof McpSchema.JSONRPCRequest jsonRpcRequest)
        || !McpSchema.METHOD_TOOLS_CALL.equals(jsonRpcRequest.method())
        || jsonRpcRequest.params() != state.requestParameters) {
      return message;
    }

    try {
      state.request.setRequestId(jsonRpcRequest.id().toString());
      McpSchema.CallToolRequest parameters = state.requestParameters;
      Map<String, Object> meta =
          parameters.meta() == null ? new HashMap<>() : new HashMap<>(parameters.meta());
      propagators()
          .getTextMapPropagator()
          .inject(context, meta, (carrier, key, value) -> carrier.put(key, value));
      return new McpSchema.JSONRPCRequest(
          jsonRpcRequest.jsonrpc(),
          jsonRpcRequest.method(),
          jsonRpcRequest.id(),
          new McpSchema.CallToolRequest(parameters.name(), parameters.arguments(), meta));
    } catch (Throwable t) {
      logger.log(FINE, "Failed to inject MCP tool call context", t);
      return message;
    }
  }
}
