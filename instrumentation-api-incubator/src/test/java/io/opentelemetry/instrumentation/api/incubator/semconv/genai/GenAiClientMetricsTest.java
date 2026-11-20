/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GenAiClientMetricsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  @SuppressWarnings("deprecation")
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener listener = GenAiClientMetrics.get().create(meterProvider.get("test"));

    Context context = listener.onStart(Context.root(), Attributes.empty(), 0);
    listener.onEnd(
        context,
        Attributes.builder()
            .put(GEN_AI_USAGE_INPUT_TOKENS, 8)
            .put(GEN_AI_USAGE_OUTPUT_TOKENS, 13)
            .build(),
        SECONDS.toNanos(2));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("gen_ai.client.token.usage")
                    .hasDescription("Number of input and output tokens used.")
                    .hasUnit("{token}")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(8)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_TOKEN_TYPE, "input")),
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(13)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(GEN_AI_TOKEN_TYPE, "output")))),
            metric ->
                assertThat(metric)
                    .hasName("gen_ai.client.operation.duration")
                    .hasDescription("GenAI operation duration.")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(2)
                                        .hasAttributes(Attributes.empty()))));
  }
}
