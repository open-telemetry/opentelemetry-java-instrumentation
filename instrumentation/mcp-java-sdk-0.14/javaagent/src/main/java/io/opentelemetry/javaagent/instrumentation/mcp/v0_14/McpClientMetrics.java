/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.incubating.McpIncubatingMetrics.MCP_CLIENT_OPERATION_DURATION_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.McpIncubatingMetrics.MCP_CLIENT_OPERATION_DURATION_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingMetrics.MCP_CLIENT_OPERATION_DURATION_UNIT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import java.util.logging.Logger;

final class McpClientMetrics implements OperationListener {
  private static final double NANOS_PER_S = SECONDS.toNanos(1);
  private static final ContextKey<State> STATE_KEY = ContextKey.named("mcp-client-metrics-state");
  private static final Logger logger = Logger.getLogger(McpClientMetrics.class.getName());

  static OperationMetrics get() {
    return OperationMetricsUtil.create("mcp client", McpClientMetrics::new);
  }

  private final DoubleHistogram operationDuration;

  @SuppressWarnings("deprecation") // using deprecated semconv
  private McpClientMetrics(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder(MCP_CLIENT_OPERATION_DURATION_NAME)
            .setUnit(MCP_CLIENT_OPERATION_DURATION_UNIT)
            .setDescription(MCP_CLIENT_OPERATION_DURATION_DESCRIPTION)
            .setExplicitBucketBoundariesAdvice(
                asList(
                    0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0,
                    300.0));
    if (builder instanceof ExtendedDoubleHistogramBuilder extendedBuilder) {
      extendedBuilder.setAttributesAdvice(
          asList(
              MCP_METHOD_NAME,
              ERROR_TYPE,
              GEN_AI_OPERATION_NAME,
              GEN_AI_TOOL_NAME,
              RPC_RESPONSE_STATUS_CODE,
              MCP_PROTOCOL_VERSION));
    }
    operationDuration = builder.build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(STATE_KEY, new State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(STATE_KEY);
    if (state == null) {
      logger.log(FINE, "No state present when ending MCP client operation metrics");
      return;
    }
    AttributesBuilder attributes = state.startAttributes.toBuilder().putAll(endAttributes);
    operationDuration.record(
        (endNanos - state.startNanos) / NANOS_PER_S, attributes.build(), context);
  }

  private static final class State {
    private final Attributes startAttributes;
    private final long startNanos;

    private State(Attributes startAttributes, long startNanos) {
      this.startAttributes = startAttributes;
      this.startNanos = startNanos;
    }
  }
}
