/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.function.UnaryOperator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThriftInstrumenterFactoryTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void schemaSelectionUsesThePassedOpenTelemetryInstance(boolean client) {
    InMemorySpanExporter legacySpanExporter = InMemorySpanExporter.create();
    InMemoryMetricReader legacyMetricReader = InMemoryMetricReader.create();
    InMemorySpanExporter previewSpanExporter = InMemorySpanExporter.create();
    InMemoryMetricReader previewMetricReader = InMemoryMetricReader.create();
    try (OpenTelemetrySdk legacySdk = createSdk(legacySpanExporter, legacyMetricReader);
        OpenTelemetrySdk previewSdk = createSdk(previewSpanExporter, previewMetricReader)) {
      ExtendedOpenTelemetry legacyOpenTelemetry = createOpenTelemetry(legacySdk, false);
      ExtendedOpenTelemetry previewOpenTelemetry = createOpenTelemetry(previewSdk, true);
      Instrumenter<ThriftRequest, ThriftResponse> legacyInstrumenter =
          createInstrumenter(legacyOpenTelemetry, client);
      Instrumenter<ThriftRequest, ThriftResponse> previewInstrumenter =
          createInstrumenter(previewOpenTelemetry, client);
      ThriftRequest request = new ThriftRequest("method", "Service", null);

      Context legacyContext = legacyInstrumenter.start(Context.root(), request);
      legacyInstrumenter.end(legacyContext, request, null, null);
      Context previewContext = previewInstrumenter.start(Context.root(), request);
      previewInstrumenter.end(previewContext, request, null, null);

      assertThat(legacySpanExporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0));
      assertThat(previewSpanExporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0));
      assertMetricSchema(legacyMetricReader, SchemaUrls.V1_37_0);
      assertMetricSchema(previewMetricReader, SchemaUrls.V1_44_0);
    }
  }

  private static OpenTelemetrySdk createSdk(
      InMemorySpanExporter spanExporter, InMemoryMetricReader metricReader) {
    return OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build())
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
        .build();
  }

  private static ExtendedOpenTelemetry createOpenTelemetry(OpenTelemetrySdk sdk, boolean preview) {
    DeclarativeConfigProperties general =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties semconv = general.get("rpc").get("semconv");
    when(semconv.getInt("version")).thenReturn(preview ? 1 : 0);
    when(semconv.getBoolean("experimental", false)).thenReturn(preview);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    when(configProvider.getInstrumentationConfig("common"))
        .thenReturn(DeclarativeConfigProperties.empty());
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    when(openTelemetry.getGeneralInstrumentationConfig()).thenReturn(general);
    when(openTelemetry.getInstrumentationConfig("common"))
        .thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.getTracerProvider()).thenReturn(sdk.getTracerProvider());
    when(openTelemetry.getMeterProvider()).thenReturn(sdk.getMeterProvider());
    when(openTelemetry.getLogsBridge()).thenReturn(sdk.getLogsBridge());
    when(openTelemetry.getPropagators()).thenReturn(ContextPropagators.noop());
    return openTelemetry;
  }

  private static Instrumenter<ThriftRequest, ThriftResponse> createInstrumenter(
      ExtendedOpenTelemetry openTelemetry, boolean client) {
    UnaryOperator<SpanNameExtractor<ThriftRequest>> spanNameExtractorCustomizer =
        extractor -> extractor;
    return client
        ? ThriftInstrumenterFactory.createClientInstrumenter(
            openTelemetry, spanNameExtractorCustomizer, emptyList(), emptyList())
        : ThriftInstrumenterFactory.createServerInstrumenter(
            openTelemetry, spanNameExtractorCustomizer, emptyList(), emptyList());
  }

  private static void assertMetricSchema(InMemoryMetricReader metricReader, String schemaUrl) {
    assertThat(metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(hasScopeSchemaUrl(schemaUrl));
  }
}
