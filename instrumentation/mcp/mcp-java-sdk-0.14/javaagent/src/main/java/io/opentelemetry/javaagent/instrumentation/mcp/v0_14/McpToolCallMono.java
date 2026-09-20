/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcp.v0_14.McpSingletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.reactor.v3_1.ReactorAsyncOperationEndStrategy;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;

final class McpToolCallMono {
  private static final Logger logger = Logger.getLogger(McpToolCallMono.class.getName());
  private static final ReactorAsyncOperationEndStrategy endStrategy =
      ReactorAsyncOperationEndStrategy.create();

  static Mono<?> wrap(Mono<?> publisher, McpToolCallState state) {
    return Mono.deferContextual(
        reactorContext -> {
          try {
            Context parentContext =
                ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                    reactorContext, Context.current());
            McpToolCallRequest request = state.createRequest();
            if (!instrumenter().shouldStart(parentContext, request)) {
              return publisher;
            }

            Context context = instrumenter().start(parentContext, request);
            state.inject(context);
            try {
              return (Mono<?>)
                  endStrategy.end(
                      instrumenter(), context, request, publisher, McpSchema.CallToolResult.class);
            } catch (Throwable t) {
              logger.log(FINE, "Failed to wrap MCP tool call publisher", t);
              endSpan(context, request);
            }
          } catch (Throwable t) {
            logger.log(FINE, "Failed to trace MCP tool call", t);
            // Ignore instrumentation failures to preserve application behavior.
          }
          return publisher;
        });
  }

  private static void endSpan(Context context, McpToolCallRequest request) {
    try {
      instrumenter().end(context, request, null, null);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to end MCP tool call span", t);
      // Ignore instrumentation failures to preserve application behavior.
    }
  }

  private McpToolCallMono() {}
}
