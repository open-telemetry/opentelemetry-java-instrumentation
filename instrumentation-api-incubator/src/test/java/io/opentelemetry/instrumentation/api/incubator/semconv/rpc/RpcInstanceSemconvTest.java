/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAME;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAMESPACE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // old semconv and dual-emission customizer
class RpcInstanceSemconvTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void instancesEmitIndependentSemconv(boolean client) {
    List<ConfiguredInstrumenter> instances = new ArrayList<>();
    for (int mode = 0; mode < 3; mode++) {
      instances.add(createInstrumenter(client, mode != 0, mode == 2));
    }

    for (int mode = 0; mode < instances.size(); mode++) {
      ConfiguredInstrumenter instance = instances.get(mode);
      boolean preview = mode != 0;
      boolean old = mode != 1;
      String schemaUrl = preview ? SchemaUrls.V1_44_0 : SchemaUrls.V1_37_0;
      Context context = instance.instrumenter.start(Context.root(), "request");
      instance.instrumenter.end(context, "request", null, new IllegalStateException());

      assertThat(instance.spanExporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(
              span ->
                  assertThat(span)
                      .hasName(preview ? "Service/Method" : "package.Service/Method")
                      .hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM, old ? "test" : null),
                          equalTo(RPC_SERVICE, old ? "package.Service" : null),
                          equalTo(RPC_SYSTEM_NAME, preview ? "test" : null),
                          equalTo(RPC_METHOD, preview ? "Service/Method" : "Method"),
                          equalTo(PEER_SERVICE, old ? "peer" : null),
                          equalTo(SERVICE_PEER_NAME, preview ? "peer" : null),
                          equalTo(SERVICE_PEER_NAMESPACE, preview ? "namespace" : null),
                          equalTo(ERROR_TYPE, preview ? "java.lang.IllegalStateException" : null)));
      assertThat(instance.spanExporter.getFinishedSpanItems().get(0).getInstrumentationScopeInfo())
          .satisfies(scope -> assertThat(scope.getSchemaUrl()).isEqualTo(schemaUrl));

      Collection<MetricData> metrics = instance.metricReader.collectAllMetrics();
      assertThat(metrics).hasSize(mode == 2 ? 2 : 1);
      String prefix = client ? "rpc.client" : "rpc.server";
      if (old) {
        assertThat(metrics)
            .anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName(prefix + ".duration")
                        .hasUnit("ms")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(RPC_SYSTEM, "test"),
                                                equalTo(RPC_SERVICE, "package.Service"),
                                                equalTo(RPC_METHOD, "Method")))));
      }
      if (preview) {
        assertThat(metrics)
            .anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName(prefix + ".call.duration")
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(RPC_SYSTEM_NAME, "test"),
                                                equalTo(RPC_METHOD, "Service/Method")))));
      }
      assertThat(metrics)
          .allSatisfy(
              metric ->
                  assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                      .isEqualTo(schemaUrl));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void nestedOldOnlyOperationDoesNotInheritParentMethod(boolean client) {
    ConfiguredInstrumenter parent = createInstrumenter(!client, true, true);
    ConfiguredInstrumenter child = createInstrumenter(client, false, false);
    Context parentContext = parent.instrumenter.start(Context.root(), "parent");
    Context childContext = child.instrumenter.start(parentContext, "request");
    child.instrumenter.end(childContext, "request", null, null);
    parent.instrumenter.end(parentContext, "parent", null, null);

    assertThat(child.metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasName(client ? "rpc.client.duration" : "rpc.server.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point.hasAttributesSatisfyingExactly(
                                        equalTo(RPC_SYSTEM, "test"),
                                        equalTo(RPC_SERVICE, "package.Service"),
                                        equalTo(RPC_METHOD, "Method")))));
  }

  private ConfiguredInstrumenter createInstrumenter(
      boolean client, boolean preview, boolean dualEmit) {
    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build())
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build();
    cleanup.deferCleanup(sdk);

    DeclarativeConfigProperties general =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties semconv = general.get("rpc").get("semconv");
    when(semconv.getInt("version")).thenReturn(preview ? 1 : 0);
    when(semconv.getBoolean("experimental", false)).thenReturn(preview);
    when(semconv.getBoolean("dual_emit", false)).thenReturn(dualEmit);
    DeclarativeConfigProperties common =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties previewConfig = common.get("semconv_stability");
    when(previewConfig.getPropertyKeys()).thenReturn(singleton("preview"));
    when(previewConfig.getScalarList("preview", String.class, emptyList()))
        .thenReturn(
            preview ? singletonList(dualEmit ? "service.peer/dup" : "service.peer") : emptyList());
    DeclarativeConfigProperties mapping = mock(DeclarativeConfigProperties.class);
    when(mapping.getString("peer")).thenReturn("example.com");
    when(mapping.getString("service_name")).thenReturn("peer");
    when(mapping.getString("service_namespace")).thenReturn("namespace");
    when(common.getStructuredList("service_peer_mapping", emptyList()))
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

    RpcAttributesGetter<String, Void> getter =
        new RpcAttributesGetter<String, Void>() {
          @Override
          public String getSystem(String request) {
            return "test";
          }

          @Override
          public String getService(String request) {
            return "package.Service";
          }

          @Override
          public String getMethod(String request) {
            return request.equals("parent") ? "ParentMethod" : "Method";
          }

          @Override
          public String getRpcSystemName(String request) {
            return "test";
          }

          @Override
          public String getRpcMethod(String request) {
            return "Service/" + getMethod(request);
          }
        };
    InstrumenterBuilder<String, Void> builder =
        Instrumenter.<String, Void>builder(
                openTelemetry, "test", RpcSpanNameExtractor.create(openTelemetry, getter))
            .addAttributesExtractor(
                client
                    ? RpcClientAttributesExtractor.create(openTelemetry, getter)
                    : RpcServerAttributesExtractor.create(openTelemetry, getter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new ServerAttributesGetter<String>() {
                      @Override
                      public String getServerAddress(String request) {
                        return "example.com";
                      }
                    },
                    openTelemetry))
            .addContextCustomizer(
                RpcMetricsContextCustomizers.dualEmitContextCustomizer(openTelemetry, getter))
            .addOperationMetrics(
                client ? RpcClientMetrics.get(openTelemetry) : RpcServerMetrics.get(openTelemetry));
    return new ConfiguredInstrumenter(
        builder.buildInstrumenter(
            client ? SpanKindExtractor.alwaysClient() : SpanKindExtractor.alwaysServer()),
        spanExporter,
        metricReader);
  }

  private static final class ConfiguredInstrumenter {
    final Instrumenter<String, Void> instrumenter;
    final InMemorySpanExporter spanExporter;
    final InMemoryMetricReader metricReader;

    ConfiguredInstrumenter(
        Instrumenter<String, Void> instrumenter,
        InMemorySpanExporter spanExporter,
        InMemoryMetricReader metricReader) {
      this.instrumenter = instrumenter;
      this.spanExporter = spanExporter;
      this.metricReader = metricReader;
    }
  }
}
