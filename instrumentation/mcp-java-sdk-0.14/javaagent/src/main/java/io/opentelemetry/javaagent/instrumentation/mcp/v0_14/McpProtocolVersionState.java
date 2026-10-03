/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static java.util.logging.Level.FINE;

import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class McpProtocolVersionState {
  private static final Logger logger = Logger.getLogger(McpProtocolVersionState.class.getName());
  private static final VirtualField<McpClientSession, McpProtocolVersionState>
      SESSION_PROTOCOL_VERSION =
          VirtualField.find(McpClientSession.class, McpProtocolVersionState.class);

  private final String protocolVersion;

  @Nullable
  public static String get(McpClientSession session) {
    McpProtocolVersionState state = SESSION_PROTOCOL_VERSION.get(session);
    return state == null ? null : state.protocolVersion;
  }

  @Nullable
  public static Mono<?> capture(
      McpClientSession session, String method, @Nullable Mono<?> publisher) {
    if (!McpSchema.METHOD_INITIALIZE.equals(method) || publisher == null) {
      return publisher;
    }
    return publisher.doOnNext(
        result -> {
          try {
            if (result instanceof McpSchema.InitializeResult initializeResult
                && initializeResult.protocolVersion() != null) {
              SESSION_PROTOCOL_VERSION.set(
                  session, new McpProtocolVersionState(initializeResult.protocolVersion()));
            }
          } catch (Throwable t) {
            logger.log(FINE, "Failed to capture MCP protocol version", t);
          }
        });
  }

  private McpProtocolVersionState(String protocolVersion) {
    this.protocolVersion = protocolVersion;
  }
}
