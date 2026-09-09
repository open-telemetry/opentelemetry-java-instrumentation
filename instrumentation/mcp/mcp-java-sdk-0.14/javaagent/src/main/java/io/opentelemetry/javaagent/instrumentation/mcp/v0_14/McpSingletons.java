/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

final class McpSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.mcp-java-sdk-0.14";

  private static final Instrumenter<McpToolCallRequest, McpSchema.CallToolResult> instrumenter =
      Instrumenter.<McpToolCallRequest, McpSchema.CallToolResult>builder(
              GlobalOpenTelemetry.get(),
              INSTRUMENTATION_NAME,
              request -> McpSchema.METHOD_TOOLS_CALL + " " + request.getToolName())
          .addAttributesExtractor(new McpToolCallAttributesExtractor())
          .setSpanStatusExtractor(
              (spanStatusBuilder, request, response, error) -> {
                if (response != null && Boolean.TRUE.equals(response.isError())) {
                  spanStatusBuilder.setStatus(StatusCode.ERROR);
                } else if (error instanceof McpError && error.getMessage() != null) {
                  spanStatusBuilder.setStatus(StatusCode.ERROR, error.getMessage());
                } else {
                  SpanStatusExtractor.getDefault()
                      .extract(spanStatusBuilder, request, response, error);
                }
              })
          .buildInstrumenter(SpanKindExtractor.alwaysClient());

  private static final ContextPropagators propagators = GlobalOpenTelemetry.get().getPropagators();

  static Instrumenter<McpToolCallRequest, McpSchema.CallToolResult> instrumenter() {
    return instrumenter;
  }

  static ContextPropagators propagators() {
    return propagators;
  }

  private McpSingletons() {}
}
