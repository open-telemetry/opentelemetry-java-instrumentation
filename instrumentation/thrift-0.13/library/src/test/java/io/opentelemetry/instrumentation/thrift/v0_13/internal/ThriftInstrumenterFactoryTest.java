/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThriftInstrumenterFactoryTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @SuppressWarnings("deprecation")
  void schemaAndCustomizedNamesUseThePassedInstancesRpcMode(boolean client) {
    for (int mode = 0; mode < 3; mode++) {
      boolean preview = mode != 0;
      boolean dualEmit = mode == 2;
      InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
      InMemoryMetricReader metricReader = InMemoryMetricReader.create();
      try (OpenTelemetrySdk sdk =
          OpenTelemetrySdk.builder()
              .setTracerProvider(
                  SdkTracerProvider.builder()
                      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                      .build())
              .setMeterProvider(
                  SdkMeterProvider.builder().registerMetricReader(metricReader).build())
              .build()) {
        DeclarativeConfigProperties general =
            mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
        DeclarativeConfigProperties semconv = general.get("rpc").get("semconv");
        when(semconv.getInt("version")).thenReturn(preview ? 1 : 0);
        when(semconv.getBoolean("experimental", false)).thenReturn(preview);
        when(semconv.getBoolean("dual_emit", false)).thenReturn(dualEmit);
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

        Instrumenter<ThriftRequest, ThriftResponse> instrumenter =
            client
                ? ThriftInstrumenterFactory.createClientInstrumenter(
                    openTelemetry,
                    extractor -> request -> "custom " + extractor.extract(request),
                    emptyList(),
                    emptyList())
                : ThriftInstrumenterFactory.createServerInstrumenter(
                    openTelemetry,
                    extractor -> request -> "custom " + extractor.extract(request),
                    emptyList(),
                    emptyList());
        ThriftRequest request = new ThriftRequest("method", "Service", null);
        Context context = instrumenter.start(Context.root(), request);
        instrumenter.end(context, request, null, null);

        assertThat(spanExporter.getFinishedSpanItems())
            .singleElement()
            .satisfies(
                span -> {
                  assertThat(span.getName()).isEqualTo("custom Service/method");
                  assertThat(span.getInstrumentationScopeInfo().getSchemaUrl())
                      .isEqualTo(
                          preview
                              ? "https://opentelemetry.io/schemas/1.44.0"
                              : "https://opentelemetry.io/schemas/1.37.0");
                  assertThat(span.getAttributes().get(RPC_SYSTEM))
                      .isEqualTo(!preview || dualEmit ? "apache_thrift" : null);
                  assertThat(span.getAttributes().get(RPC_SYSTEM_NAME))
                      .isEqualTo(preview ? "apache_thrift" : null);
                  assertThat(span.getAttributes().get(RPC_METHOD))
                      .isEqualTo(preview ? "Service/method" : "method");
                });
        Collection<MetricData> metrics = metricReader.collectAllMetrics();
        String prefix = client ? "rpc.client" : "rpc.server";
        assertThat(metrics)
            .extracting(MetricData::getName)
            .containsExactlyInAnyOrderElementsOf(
                dualEmit
                    ? asList(prefix + ".duration", prefix + ".call.duration")
                    : singletonList(prefix + (preview ? ".call.duration" : ".duration")));
        assertThat(metrics)
            .allSatisfy(
                metric ->
                    assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                        .isEqualTo(
                            preview
                                ? "https://opentelemetry.io/schemas/1.44.0"
                                : "https://opentelemetry.io/schemas/1.37.0"));
      }
    }
  }
}
