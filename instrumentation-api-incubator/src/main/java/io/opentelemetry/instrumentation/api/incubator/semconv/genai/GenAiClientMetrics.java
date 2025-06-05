/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_USAGE_OUTPUT_TOKENS;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/#generative-ai-client-metrics">Generative
 * AI Client Metrics</a>.
 */
public final class GenAiClientMetrics implements OperationListener {

  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  private static final ContextKey<State> GEN_AI_CLIENT_METRICS_STATE =
      ContextKey.named("gen-ai-client-metrics-state");

  private static final Logger logger = Logger.getLogger(DbClientMetrics.class.getName());

  static final AttributeKey<String> GEN_AI_TOKEN_TYPE = stringKey("gen_ai.token.type");

  /**
   * Returns an {@link OperationMetrics} instance which can be used to enable recording of {@link
   * GenAiClientMetrics}.
   *
   * @see
   *     io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder#addOperationMetrics(OperationMetrics)
   */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create("gen_ai client", GenAiClientMetrics::new);
  }

  private final LongHistogram tokenUsage;
  private final DoubleHistogram operationDuration;

  private GenAiClientMetrics(Meter meter) {
    LongHistogramBuilder tokenUsageBuilder =
        meter
            .histogramBuilder("gen_ai.client.token.usage")
            .ofLongs()
            .setUnit("{token}")
            .setDescription("Measures number of input and output tokens used.")
            .setExplicitBucketBoundariesAdvice(GenAiMetricsAdvice.CLIENT_TOKEN_USAGE_BUCKETS);
    GenAiMetricsAdvice.applyClientTokenUsageAdvice(tokenUsageBuilder);
    this.tokenUsage = tokenUsageBuilder.build();
    DoubleHistogramBuilder operationDurationBuilder =
        meter
            .histogramBuilder("gen_ai.client.operation.duration")
            .setUnit("s")
            .setDescription("GenAI operation duration.")
            .setExplicitBucketBoundariesAdvice(
                GenAiMetricsAdvice.CLIENT_OPERATION_DURATION_BUCKETS);
    GenAiMetricsAdvice.applyClientOperationDurationAdvice(operationDurationBuilder);
    this.operationDuration = operationDurationBuilder.build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        GEN_AI_CLIENT_METRICS_STATE,
        new AutoValue_GenAiClientMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(GEN_AI_CLIENT_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record gen_ai operation metrics.",
          context);
      return;
    }

    AttributesBuilder attributesBuilder = state.startAttributes().toBuilder().putAll(endAttributes);

    operationDuration.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_S, attributesBuilder.build(), context);

    Long inputTokens = endAttributes.get(GEN_AI_USAGE_INPUT_TOKENS);
    if (inputTokens != null) {
      tokenUsage.record(
          inputTokens, attributesBuilder.put(GEN_AI_TOKEN_TYPE, "input").build(), context);
    }
    Long outputTokens = endAttributes.get(GEN_AI_USAGE_OUTPUT_TOKENS);
    if (outputTokens != null) {
      tokenUsage.record(
          outputTokens, attributesBuilder.put(GEN_AI_TOKEN_TYPE, "output").build(), context);
    }
  }

  @AutoValue
  abstract static class State {
    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
