/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
                    .hasHistogramPointsSatisfying(
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
                                    equalTo(GEN_AI_TOKEN_TYPE, "output"))),
            metric ->
                assertThat(metric)
                    .hasName("gen_ai.client.operation.duration")
                    .hasDescription("GenAI operation duration.")
                    .hasUnit("s")
                    .hasHistogramPointsSatisfying(
                        point -> point.hasCount(1).hasSum(2).hasAttributes(Attributes.empty())));
  }

  @ParameterizedTest
  @CsvSource({
    "chat, gen_ai.client.inference.duration",
    "generate_content, gen_ai.client.inference.duration",
    "text_completion, gen_ai.client.inference.duration",
    "embeddings, gen_ai.client.operation.duration",
  })
  void recordsInferenceDurationWhenGenAiPreviewIsConfigured(
      String operationName, String expectedMetricName) {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(
                genAiPreviewOpenTelemetry(meterProvider), "test", request -> request)
            .addAttributesExtractor(
                AttributesExtractor.constant(GEN_AI_OPERATION_NAME, operationName))
            .addOperationMetrics(GenAiClientMetrics.get())
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), operationName);
    instrumenter.end(context, operationName, null, null);

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactly(metric ->
            assertThat(metric)
                .hasName(expectedMetricName)
                .hasUnit("s")
                .hasHistogramPointsSatisfying(point ->
                    point
                        .hasCount(1)
                        .hasAttributesSatisfyingExactly(
                            equalTo(GEN_AI_OPERATION_NAME, operationName))));
  }

  private static OpenTelemetry genAiPreviewOpenTelemetry(SdkMeterProvider meterProvider) {
    DeclarativeConfigProperties semconvStability = mock(DeclarativeConfigProperties.class);
    when(semconvStability.getPropertyKeys()).thenReturn(singleton("preview"));
    when(semconvStability.getScalarList("preview", String.class, emptyList()))
        .thenReturn(singletonList("gen_ai"));
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(commonConfig.get("semconv_stability")).thenReturn(semconvStability);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    when(configProvider.getInstrumentationConfig("common")).thenReturn(commonConfig);

    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getTracerProvider()).thenReturn(TracerProvider.noop());
    when(openTelemetry.getMeterProvider()).thenReturn(meterProvider);
    when(openTelemetry.getLogsBridge()).thenReturn(LoggerProvider.noop());
    return openTelemetry;
  }
}
