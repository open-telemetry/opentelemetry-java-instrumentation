/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcp.v0_14.McpSingletons.propagators;
import static io.opentelemetry.javaagent.instrumentation.mcp.v0_14.McpSingletons.serverInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.reactor.v3_1.ReactorAsyncOperationEndStrategy;
import reactor.core.publisher.Mono;

final class McpServerToolCallMono {
  private static final ReactorAsyncOperationEndStrategy endStrategy =
      ReactorAsyncOperationEndStrategy.create();

  static Mono<?> wrap(Mono<?> publisher, McpServerToolCallRequest request) {
    return Mono.deferContextual(
        reactorContext -> {
          try {
            Context ambientContext =
                ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                    reactorContext, Context.current());
            request.setAmbientContext(ambientContext);
            Context parentContext =
                propagators()
                    .getTextMapPropagator()
                    .extract(Context.root(), request.getMeta(), McpMetaTextMapGetter.INSTANCE);
            if (!serverInstrumenter().shouldStart(parentContext, request)) {
              return publisher;
            }

            Context context = serverInstrumenter().start(parentContext, request);
            try {
              return (Mono<?>)
                  endStrategy.end(serverInstrumenter(), context, request, publisher, Void.class);
            } catch (Throwable ignored) {
              try {
                serverInstrumenter().end(context, request, null, null);
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

  private McpServerToolCallMono() {}
}
