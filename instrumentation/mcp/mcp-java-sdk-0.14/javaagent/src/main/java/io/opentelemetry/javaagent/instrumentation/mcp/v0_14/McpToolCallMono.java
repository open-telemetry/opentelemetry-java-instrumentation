/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcp.v0_14.McpSingletons.instrumenter;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.reactor.v3_1.ReactorAsyncOperationEndStrategy;
import reactor.core.publisher.Mono;

final class McpToolCallMono {
  private static final ReactorAsyncOperationEndStrategy endStrategy =
      ReactorAsyncOperationEndStrategy.create();

  static Mono<?> wrap(Mono<?> publisher, McpToolCallState state) {
    return Mono.deferContextual(
        reactorContext -> {
          try {
            Context parentContext =
                ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                    reactorContext, Context.current());
            if (!instrumenter().shouldStart(parentContext, state.getRequest())) {
              return publisher;
            }

            Context context = instrumenter().start(parentContext, state.getRequest());
            state.inject(context);
            try {
              return (Mono<?>)
                  endStrategy.end(
                      instrumenter(),
                      context,
                      state.getRequest(),
                      publisher,
                      McpSchema.CallToolResult.class);
            } catch (Throwable ignored) {
              try {
                instrumenter().end(context, state.getRequest(), null, null);
              } catch (Throwable ignoredEnd) {
                // Ignore instrumentation failures to preserve application behavior.
              }
            }
          } catch (Throwable ignored) {
            // Ignore instrumentation failures to preserve application behavior.
          }
          return publisher;
        });
  }

  private McpToolCallMono() {}
}
