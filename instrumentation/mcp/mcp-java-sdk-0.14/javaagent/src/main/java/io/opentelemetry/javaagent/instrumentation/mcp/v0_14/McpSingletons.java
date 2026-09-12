/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
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

  private static final Instrumenter<McpServerToolCallRequest, Void> serverInstrumenter =
      Instrumenter.<McpServerToolCallRequest, Void>builder(
              GlobalOpenTelemetry.get(),
              INSTRUMENTATION_NAME,
              request -> {
                String toolName = request.getToolName();
                return toolName == null
                    ? McpSchema.METHOD_TOOLS_CALL
                    : McpSchema.METHOD_TOOLS_CALL + " " + toolName;
              })
          .addAttributesExtractor(new McpServerToolCallAttributesExtractor())
          .addSpanLinksExtractor(McpSingletons::extractServerSpanLink)
          .setSpanStatusExtractor(
              (spanStatusBuilder, request, response, error) -> {
                if (error != null) {
                  SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, null, error);
                } else if (request.isToolError()) {
                  spanStatusBuilder.setStatus(StatusCode.ERROR);
                } else if (request.isServerJsonRpcError()) {
                  spanStatusBuilder.setStatus(StatusCode.ERROR, request.getResponseErrorMessage());
                }
              })
          .buildInstrumenter(SpanKindExtractor.alwaysServer());

  private static final ContextPropagators propagators = GlobalOpenTelemetry.get().getPropagators();

  static Instrumenter<McpToolCallRequest, McpSchema.CallToolResult> instrumenter() {
    return instrumenter;
  }

  static Instrumenter<McpServerToolCallRequest, Void> serverInstrumenter() {
    return serverInstrumenter;
  }

  static ContextPropagators propagators() {
    return propagators;
  }

  private static void extractServerSpanLink(
      SpanLinksBuilder spanLinks, Context parentContext, McpServerToolCallRequest request) {
    SpanContext ambientSpanContext = Span.fromContext(request.getAmbientContext()).getSpanContext();
    SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
    if (ambientSpanContext.isValid() && !ambientSpanContext.equals(parentSpanContext)) {
      spanLinks.addLink(ambientSpanContext);
    }
  }

  private McpSingletons() {}
}
