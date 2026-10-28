/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.Collection;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RpcInstanceSemconvTest {

  private static final double[] DURATION_BUCKETS = {
    0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5, 10, 50, 100
  };

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void defaultSdkEmitsCanonicalRpcTelemetry(boolean client) {
    ConfiguredInstrumenter instance = new ConfiguredInstrumenter(client, true);
    Context context = instance.instrumenter.start(Context.root(), "request");
    instance.instrumenter.end(context, "request", null, null);

    assertThat(instance.spanExporter.getFinishedSpanItems())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span)
                  .hasName("Service/Method")
                  .hasAttributesSatisfyingExactly(
                      equalTo(RPC_SYSTEM_NAME, "test"), equalTo(RPC_METHOD, "Service/Method"));
              assertThat(span.getInstrumentationScopeInfo().getSchemaUrl())
                  .isEqualTo(SchemaUrls.V1_44_0);
            });
    assertThat(instance.metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric -> {
              assertThat(metric)
                  .hasName(client ? "rpc.client.call.duration" : "rpc.server.call.duration")
                  .hasUnit("s")
                  .hasHistogramSatisfying(
                      histogram ->
                          histogram.hasPointsSatisfying(
                              point ->
                                  point
                                      .hasCount(1)
                                      .hasBucketBoundaries(DURATION_BUCKETS)
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "test"),
                                          equalTo(RPC_METHOD, "Service/Method"))));
              assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                  .isEqualTo(SchemaUrls.V1_44_0);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void instancesUseRpcConventionsAndServerServiceMapping(boolean client) {
    ConfiguredInstrumenter instance = new ConfiguredInstrumenter(client, false);
    Context context = instance.instrumenter.start(Context.root(), "request");
    instance.instrumenter.end(context, "request", null, new IllegalStateException());

    assertThat(instance.spanExporter.getFinishedSpanItems())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span)
                    .hasName("Service/Method")
                    .hasAttributesSatisfyingExactly(
                        equalTo(RPC_SYSTEM_NAME, "test"),
                        equalTo(RPC_METHOD, "Service/Method"),
                        equalTo(stringKey("server.service.name"), "payments"),
                        equalTo(stringKey("server.service.namespace"), "checkout"),
                        equalTo(stringKey("rpc.status_code"), "CANCELLED"),
                        equalTo(ERROR_TYPE, "java.lang.IllegalStateException")));
    assertThat(instance.spanExporter.getFinishedSpanItems().get(0).getInstrumentationScopeInfo())
        .satisfies(scope -> assertThat(scope.getSchemaUrl()).isEqualTo(SchemaUrls.V1_44_0));

    Collection<MetricData> metrics = instance.metricReader.collectAllMetrics();
    assertThat(metrics)
        .extracting(MetricData::getName)
        .containsExactly(client ? "rpc.client.call.duration" : "rpc.server.call.duration");
    assertThat(metrics)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName(client ? "rpc.client.call.duration" : "rpc.server.call.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasBucketBoundaries(DURATION_BUCKETS)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(ERROR_TYPE, "java.lang.IllegalStateException"),
                                            equalTo(stringKey("rpc.status_code"), "CANCELLED"),
                                            equalTo(RPC_SYSTEM_NAME, "test"),
                                            equalTo(RPC_METHOD, "Service/Method")))));
    assertThat(metrics)
        .allSatisfy(
            metric ->
                assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                    .isEqualTo(SchemaUrls.V1_44_0));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void nestedOperationDoesNotInheritParentMethod(boolean client) {
    ConfiguredInstrumenter parent = new ConfiguredInstrumenter(!client, false);
    ConfiguredInstrumenter child = new ConfiguredInstrumenter(client, false);
    Context parentContext = parent.instrumenter.start(Context.root(), "parent");
    Context childContext = child.instrumenter.start(parentContext, "request");
    child.instrumenter.end(childContext, "request", null, null);
    parent.instrumenter.end(parentContext, "parent", null, null);

    assertThat(child.metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasName(client ? "rpc.client.call.duration" : "rpc.server.call.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point.hasAttributesSatisfyingExactly(
                                        equalTo(RPC_SYSTEM_NAME, "test"),
                                        equalTo(RPC_METHOD, "Service/Method")))));
  }

  private final class ConfiguredInstrumenter {
    final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    final InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    final Instrumenter<String, Void> instrumenter;

    ConfiguredInstrumenter(boolean client, boolean defaultConfiguration) {
      OpenTelemetrySdk sdk =
          OpenTelemetrySdk.builder()
              .setTracerProvider(
                  SdkTracerProvider.builder()
                      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                      .build())
              .setMeterProvider(
                  SdkMeterProvider.builder().registerMetricReader(metricReader).build())
              .build();
      cleanup.deferCleanup(sdk);

      DeclarativeConfigProperties general =
          mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
      DeclarativeConfigProperties common =
          mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
      DeclarativeConfigProperties mapping = mock(DeclarativeConfigProperties.class);
      when(mapping.getString("target")).thenReturn("example.com");
      when(mapping.getString("service_name")).thenReturn("payments");
      when(mapping.getString("service_namespace")).thenReturn("checkout");
      when(common.getStructuredList("server_service_mapping", emptyList()))
          .thenReturn(singletonList(mapping));
      ConfigProvider configProvider = mock(ConfigProvider.class);
      when(configProvider.getInstrumentationConfig("common")).thenReturn(common);
      ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
      when(openTelemetry.getTracerProvider()).thenReturn(sdk.getTracerProvider());
      when(openTelemetry.getMeterProvider()).thenReturn(sdk.getMeterProvider());
      when(openTelemetry.getLogsBridge()).thenReturn(sdk.getLogsBridge());
      when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
      when(openTelemetry.getGeneralInstrumentationConfig()).thenReturn(general);
      when(openTelemetry.getInstrumentationConfig("common")).thenReturn(common);
      OpenTelemetry telemetry = defaultConfiguration ? sdk : openTelemetry;

      TestGetter getter = new TestGetter();
      instrumenter =
          Instrumenter.<String, Void>builder(telemetry, "test", RpcSpanNameExtractor.create(getter))
              .setSchemaUrl(SchemaUrls.V1_44_0)
              .addAttributesExtractor(
                  client
                      ? RpcClientAttributesExtractor.create(getter)
                      : RpcServerAttributesExtractor.create(getter))
              .addOperationMetrics(client ? RpcClientMetrics.get() : RpcServerMetrics.get())
              .addAttributesExtractor(
                  ServicePeerAttributesExtractor.create(
                      new ServerAttributesGetter<String>() {
                        @Override
                        public String getServerAddress(String request) {
                          return "example.com";
                        }
                      },
                      telemetry))
              .buildInstrumenter(
                  client ? SpanKindExtractor.alwaysClient() : SpanKindExtractor.alwaysServer());
    }
  }

  private static final class TestGetter implements RpcAttributesGetter<String, Void> {
    @Override
    public String getRpcSystemName(String request) {
      return "test";
    }

    @Override
    public String getRpcMethod(String request) {
      return "Service/" + (request.equals("parent") ? "ParentMethod" : "Method");
    }

    @Override
    public String getRpcStatusCode(String request, Void response, Throwable error) {
      return error != null ? "CANCELLED" : null;
    }
  }
}
