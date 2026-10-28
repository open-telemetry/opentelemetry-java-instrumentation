/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAME;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_PEER_NAMESPACE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizerUtil;
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
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.SetSystemProperty;

@SuppressWarnings("deprecation") // old semconv and dual-emission customizer
class RpcInstanceSemconvTest {

  private static final double[] DURATION_BUCKETS = {
    0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5, 10, 50, 100
  };

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @CsvSource({"true,''", "false,''", "true,rpc/dup", "false,rpc/dup"})
  @SetSystemProperty(key = "otel.semconv-stability.preview", value = "")
  void defaultSdkEmitsOnlyNewRpcTelemetry(boolean client, String preview) {
    System.setProperty("otel.semconv-stability.preview", preview);
    ConfiguredInstrumenter instance = new ConfiguredInstrumenter(client, false, false, true);
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
  void instancesUseNewRpcConventionsAndIndependentServicePeerConventions(boolean client) {
    List<ConfiguredInstrumenter> instances =
        asList(
            new ConfiguredInstrumenter(client, false, false),
            new ConfiguredInstrumenter(client, true, false),
            new ConfiguredInstrumenter(client, true, true));

    for (int mode = 0; mode < instances.size(); mode++) {
      ConfiguredInstrumenter instance = instances.get(mode);
      boolean previewServicePeer = mode != 0;
      boolean oldServicePeer = mode != 1;
      String schemaUrl = SchemaUrls.V1_44_0;
      assertThat(instance.selection.schemaUrl()).isEqualTo(schemaUrl);
      assertThat(instance.selection.emitOld()).isFalse();
      assertThat(instance.selection.emitPreview()).isTrue();
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
                          equalTo(PEER_SERVICE, oldServicePeer ? "peer" : null),
                          equalTo(SERVICE_PEER_NAME, previewServicePeer ? "peer" : null),
                          equalTo(SERVICE_PEER_NAMESPACE, previewServicePeer ? "namespace" : null),
                          equalTo(stringKey("rpc.status_code"), "CANCELLED"),
                          equalTo(ERROR_TYPE, "java.lang.IllegalStateException")));
      assertThat(instance.spanExporter.getFinishedSpanItems().get(0).getInstrumentationScopeInfo())
          .satisfies(scope -> assertThat(scope.getSchemaUrl()).isEqualTo(schemaUrl));

      Collection<MetricData> metrics = instance.metricReader.collectAllMetrics();
      String prefix = client ? "rpc.client" : "rpc.server";
      assertThat(metrics)
          .extracting(MetricData::getName)
          .containsExactly(prefix + ".call.duration");
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
                                          .hasBucketBoundaries(DURATION_BUCKETS)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(
                                                  ERROR_TYPE, "java.lang.IllegalStateException"),
                                              equalTo(stringKey("rpc.status_code"), "CANCELLED"),
                                              equalTo(RPC_SYSTEM_NAME, "test"),
                                              equalTo(RPC_METHOD, "Service/Method")))));
      assertThat(metrics)
          .allSatisfy(
              metric ->
                  assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                      .isEqualTo(schemaUrl));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void nestedOperationDoesNotInheritParentMethod(boolean client) {
    ConfiguredInstrumenter parent = new ConfiguredInstrumenter(!client, true, true);
    ConfiguredInstrumenter child = new ConfiguredInstrumenter(client, false, false);
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

  @Test
  void componentInitializationIsIdempotentAndRejectsRebinding() {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    TestGetter getter = new TestGetter();
    SpanNameExtractor<String> spanNameExtractor = RpcSpanNameExtractor.create(getter);
    AttributesExtractor<String, Void> attributesExtractor =
        RpcClientAttributesExtractor.create(getter);
    ContextCustomizer<String> contextCustomizer =
        RpcMetricsContextCustomizers.dualEmitContextCustomizer(getter);
    OperationMetrics operationMetrics = RpcClientMetrics.get();
    Instrumenter.<String, Void>builder(openTelemetry, "test", spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addContextCustomizer(contextCustomizer)
        .addOperationMetrics(operationMetrics)
        .buildInstrumenter();
    Instrumenter.<String, Void>builder(openTelemetry, "same-instance", spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .buildInstrumenter();

    assertCustomizerCanReturnOriginalSpanNameExtractor(openTelemetry, spanNameExtractor);

    assertThatThrownBy(
            () ->
                Instrumenter.<String, Void>builder(
                        mock(OpenTelemetry.class), "different-instance", unused -> "span")
                    .addAttributesExtractor(attributesExtractor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("different OpenTelemetry instance");

    assertCustomizerRebindingFails(
        customizer -> customizer.addAttributesExtractor(attributesExtractor));
    assertCustomizerRebindingFails(customizer -> customizer.addOperationMetrics(operationMetrics));
    assertCustomizerRebindingFails(
        customizer -> customizer.addContextCustomizer(contextCustomizer));
    assertCustomizerRebindingFails(
        customizer -> customizer.setSpanNameExtractorCustomizer(unused -> spanNameExtractor));

    Instrumenter.<String, Void>builder(openTelemetry, "after-rebinding", spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addContextCustomizer(contextCustomizer)
        .addOperationMetrics(operationMetrics)
        .buildInstrumenter();
  }

  @SuppressWarnings("unchecked")
  private static void assertCustomizerCanReturnOriginalSpanNameExtractor(
      OpenTelemetry openTelemetry, SpanNameExtractor<String> spanNameExtractor) {
    List<InternalInstrumenterCustomizerProvider> originalProviders =
        InternalInstrumenterCustomizerUtil.getInstrumenterCustomizerProviders();
    InternalInstrumenterCustomizerProvider provider =
        customizer ->
            ((InternalInstrumenterCustomizer<String, Void>) customizer)
                .setSpanNameExtractorCustomizer(unused -> spanNameExtractor);
    InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(singletonList(provider));
    try {
      Instrumenter.<String, Void>builder(openTelemetry, "customizer-test", spanNameExtractor)
          .buildInstrumenter();
    } finally {
      InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(originalProviders);
    }
  }

  @SuppressWarnings("unchecked")
  private static void assertCustomizerRebindingFails(
      Consumer<InternalInstrumenterCustomizer<String, Void>> registration) {
    List<InternalInstrumenterCustomizerProvider> originalProviders =
        InternalInstrumenterCustomizerUtil.getInstrumenterCustomizerProviders();
    InternalInstrumenterCustomizerProvider provider =
        customizer ->
            registration.accept((InternalInstrumenterCustomizer<String, Void>) customizer);
    InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(singletonList(provider));
    try {
      assertThatThrownBy(
              () ->
                  Instrumenter.<String, Void>builder(
                          mock(OpenTelemetry.class), "customizer-test", unused -> "span")
                      .buildInstrumenter())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("different OpenTelemetry instance");
    } finally {
      InternalInstrumenterCustomizerUtil.setInstrumenterCustomizerProviders(originalProviders);
    }
  }

  private final class ConfiguredInstrumenter {
    final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    final InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    final Instrumenter<String, Void> instrumenter;
    final RpcSemconvSelection.Selection selection;

    ConfiguredInstrumenter(boolean client, boolean preview, boolean dualEmit) {
      this(client, preview, dualEmit, false);
    }

    ConfiguredInstrumenter(
        boolean client, boolean preview, boolean dualEmit, boolean defaultConfiguration) {
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
              preview
                  ? asList(
                      dualEmit ? "rpc/dup" : "rpc", dualEmit ? "service.peer/dup" : "service.peer")
                  : emptyList());
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
      OpenTelemetry telemetry = defaultConfiguration ? sdk : openTelemetry;
      selection = RpcSemconvSelection.Selection.forOpenTelemetry(telemetry);

      TestGetter getter = new TestGetter();
      instrumenter =
          Instrumenter.<String, Void>builder(telemetry, "test", RpcSpanNameExtractor.create(getter))
              .setSchemaUrl(selection.schemaUrl())
              .addAttributesExtractor(
                  client
                      ? RpcClientAttributesExtractor.create(getter)
                      : RpcServerAttributesExtractor.create(getter))
              .addContextCustomizer(RpcMetricsContextCustomizers.dualEmitContextCustomizer(getter))
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

    @Override
    public String getRpcStatusCode(String request, Void response, Throwable error) {
      return error != null ? "CANCELLED" : null;
    }
  }
}
