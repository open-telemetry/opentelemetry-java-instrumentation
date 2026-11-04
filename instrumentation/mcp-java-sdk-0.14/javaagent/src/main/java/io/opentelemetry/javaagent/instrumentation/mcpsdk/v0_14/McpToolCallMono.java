/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14.McpSingletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.reactor.v3_1.ReactorAsyncOperationEndStrategy;
import java.util.logging.Logger;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

final class McpToolCallMono {
  private static final Logger logger = Logger.getLogger(McpToolCallMono.class.getName());
  private static final ReactorAsyncOperationEndStrategy endStrategy =
      ReactorAsyncOperationEndStrategy.create();

  static Mono<?> wrap(
      McpClientSession session, McpSchema.CallToolRequest requestParameters, Mono<?> publisher) {
    return Mono.deferContextual(
        reactorContext -> {
          try {
            Context parentContext =
                ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                    reactorContext, Context.current());
            McpToolCallState state = new McpToolCallState(session, requestParameters);
            McpToolCallRequest request = state.getRequest();
            if (!instrumenter().shouldStart(parentContext, request)) {
              return publisher;
            }

            Context context = state.storeInContext(instrumenter().start(parentContext, request));
            return (Mono<?>)
                endStrategy.end(
                    instrumenter(),
                    context,
                    request,
                    new SubscriptionContextMono(publisher, context),
                    McpSchema.CallToolResult.class);
          } catch (Throwable t) {
            logger.log(FINE, "Failed to trace MCP tool call", t);
            // Ignore instrumentation failures to preserve application behavior.
          }
          return publisher;
        });
  }

  private static final class SubscriptionContextMono extends Mono<Object> {
    private final Mono<?> publisher;
    private final Context context;

    private SubscriptionContextMono(Mono<?> publisher, Context context) {
      this.publisher = publisher;
      this.context = context;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Object> actual) {
      // Mono.create invokes its callback outside subscriber signal scopes.
      try (Scope ignored = context.makeCurrent()) {
        publisher.subscribe(actual);
      }
    }
  }

  private McpToolCallMono() {}
}
