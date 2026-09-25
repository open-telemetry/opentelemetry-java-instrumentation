/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;

class SpanSuppressionStrategyTest {

  static final Span span = Span.getInvalid();

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "none")
  void programmaticSpanSuppressionStrategyShouldOverrideDeprecatedProperty() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test");
    Experimental.setSpanSuppressionStrategy(builder, "span-kind");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "none")
  void programmaticSpanSuppressionStrategyShouldOverrideYaml() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
            withCommonConfig("none", "none", false), "test", request -> "test");
    Experimental.setSpanSuppressionStrategy(builder, "span-kind");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
  }

  @Test
  void programmaticSpanSuppressionStrategyShouldRejectNull() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test");

    assertThatThrownBy(() -> Experimental.setSpanSuppressionStrategy(builder, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("spanSuppressionStrategy");
  }

  @Test
  void programmaticSpanSuppressionStrategyShouldRejectUnknownValue() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test");

    assertThatThrownBy(() -> Experimental.setSpanSuppressionStrategy(builder, "spanKind"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unrecognized span suppression strategy: spanKind");
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "span-kind")
  void shouldUseDeprecatedProperty() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "span-kind")
  void stableYamlShouldOverrideDeprecatedYamlAndFlatProperty() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
            withCommonConfig("none", "span-kind", false), "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();

    assertThat(suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span))
        .isSameAs(Context.root());
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "span-kind")
  void deprecatedYamlShouldOverrideDeprecatedFlatProperty() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
            withCommonConfig(null, "none", false), "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();

    assertThat(suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span))
        .isSameAs(Context.root());
  }

  @Test
  void shouldUseStableYaml() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
            withCommonConfig("span-kind", null, false), "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
  }

  @Test
  void shouldUseDeprecatedYaml() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
            withCommonConfig(null, "span-kind", false), "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
  }

  @Test
  void shouldDefaultToSemconv() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test");
    @SuppressWarnings("unchecked")
    AttributesExtractor<String, String> extractor =
        mock(AttributesExtractor.class, withSettings().extraInterfaces(SpanKeyProvider.class));
    when(((SpanKeyProvider) extractor).internalGetSpanKey()).thenReturn(SpanKey.DB_CLIENT);
    builder.addAttributesExtractor(extractor);

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(SpanKey.DB_CLIENT.fromContextOrNull(context)).isSameAs(span);
    assertThat(SpanKey.KIND_CLIENT.fromContextOrNull(context)).isNull();
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "span-kind")
  void shouldIgnoreDeprecatedPropertyWhenConfiguredV3PreviewIsEnabled() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    when(configProvider.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.getBoolean("v3_preview")).thenReturn(true);

    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(openTelemetry, "test", request -> "test");
    SpanSuppressor suppressor = builder.buildSpanSuppressor();

    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);
    assertThat(context).isSameAs(Context.root());
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "span-kind")
  void shouldIgnoreDeprecatedYamlAndFlatPropertyUnderV3Preview() {
    ExtendedOpenTelemetry openTelemetry = withCommonConfig(null, "span-kind", true);
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(openTelemetry, "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();

    assertThat(suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span))
        .isSameAs(Context.root());
    DeclarativeConfigProperties commonConfig =
        openTelemetry.getConfigProvider().getInstrumentationConfig("common");
    verify(commonConfig).getString("span_suppression_strategy");
    verify(commonConfig, never()).getString("span_suppression_strategy/development");
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "none")
  void shouldUseStableYamlUnderV3Preview() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
            withCommonConfig("span-kind", "none", true), "test", request -> "test");

    SpanSuppressor suppressor = builder.buildSpanSuppressor();
    Context context = suppressor.storeInContext(Context.root(), SpanKind.CLIENT, span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
  }

  @Test
  @SetSystemProperty(
      key = "otel.instrumentation.experimental.span-suppression-strategy",
      value = "span-kind")
  void shouldWarnOnceOnlyWhenDeprecatedValueIsApplied() {
    boolean warningWasLogged =
        InstrumenterBuilder.spanSuppressionPropertyWarningLogged.getAndSet(false);
    List<LogRecord> records = new ArrayList<>();
    Logger logger = Logger.getLogger(InstrumenterBuilder.class.getName());
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      ExtendedOpenTelemetry stable = withCommonConfig("none", "span-kind", false);
      Instrumenter.<String, String>builder(stable, "test", request -> "test").buildSpanSuppressor();
      ExtendedOpenTelemetry preview = withCommonConfig(null, "span-kind", true);
      Instrumenter.<String, String>builder(preview, "test", request -> "test")
          .buildSpanSuppressor();
      assertThat(records).isEmpty();

      ExtendedOpenTelemetry legacy = withCommonConfig(null, "span-kind", false);
      Instrumenter.<String, String>builder(legacy, "test", request -> "test").buildSpanSuppressor();
      Instrumenter.<String, String>builder(legacy, "test", request -> "test").buildSpanSuppressor();
      assertThat(records).hasSize(1);
      assertThat(records.get(0).getLevel()).isEqualTo(WARNING);
      assertThat(records.get(0).getMessage())
          .contains("java.common.span_suppression_strategy/development")
          .contains("java.common.span_suppression_strategy")
          .contains("3.0");

      records.clear();
      InstrumenterBuilder.spanSuppressionPropertyWarningLogged.set(false);
      Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test")
          .buildSpanSuppressor();
      Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", request -> "test")
          .buildSpanSuppressor();
      assertThat(records).hasSize(1);
      assertThat(records.get(0).getMessage())
          .contains("otel.instrumentation.experimental.span-suppression-strategy");
    } finally {
      logger.removeHandler(handler);
      InstrumenterBuilder.spanSuppressionPropertyWarningLogged.set(warningWasLogged);
    }
  }

  private static ExtendedOpenTelemetry withCommonConfig(
      String stable, String deprecated, boolean v3Preview) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    ConfigProvider configProvider = mock(ConfigProvider.class);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
    when(configProvider.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.getString("span_suppression_strategy")).thenReturn(stable);
    when(commonConfig.getString("span_suppression_strategy/development")).thenReturn(deprecated);
    if (v3Preview) {
      when(commonConfig.getBoolean("v3_preview")).thenReturn(true);
    }
    return openTelemetry;
  }

  @ParameterizedTest
  @MethodSource("configArgs")
  void shouldParseConfig(String value, SpanSuppressionStrategy expectedStrategy) {
    assertThat(SpanSuppressionStrategy.fromConfig(value)).isEqualTo(expectedStrategy);
  }

  private static Stream<Arguments> configArgs() {
    return Stream.of(
        Arguments.of("none", SpanSuppressionStrategy.NONE),
        Arguments.of("NONE", SpanSuppressionStrategy.NONE),
        Arguments.of("span-kind", SpanSuppressionStrategy.SPAN_KIND),
        Arguments.of("Span-Kind", SpanSuppressionStrategy.SPAN_KIND),
        Arguments.of("semconv", SpanSuppressionStrategy.SEMCONV),
        Arguments.of("SemConv", SpanSuppressionStrategy.SEMCONV),
        Arguments.of("asdfasdfasdf", SpanSuppressionStrategy.SEMCONV),
        Arguments.of(null, SpanSuppressionStrategy.SEMCONV));
  }

  @ParameterizedTest
  @MethodSource("spanKindsAndKeys")
  void none_shouldNotSuppressAnything(SpanKind spanKind, SpanKey spanKey) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.NONE.create(emptySet());

    Context context = spanKey.storeInContext(Context.root(), span);

    assertThat(suppressor.shouldSuppress(context, spanKind)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = SpanKind.class)
  void none_shouldNotStoreSpansInContext(SpanKind spanKind) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.NONE.create(emptySet());
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, spanKind, span);

    assertThat(context).isSameAs(newContext);
  }

  @ParameterizedTest
  @MethodSource("spanKindsAndKeys")
  void spanKind_shouldStoreInContext(SpanKind spanKind, SpanKey spanKey) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.SPAN_KIND.create(emptySet());
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, spanKind, span);

    assertThat(context).isNotSameAs(newContext);
    assertThat(spanKey.fromContextOrNull(newContext)).isSameAs(span);
  }

  @ParameterizedTest
  @MethodSource("spanKindsAndKeys")
  void spanKind_shouldSuppressSameKind(SpanKind spanKind, SpanKey spanKey) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.SPAN_KIND.create(emptySet());
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, spanKind, span);

    assertThat(context).isNotSameAs(newContext);
    assertThat(spanKey.fromContextOrNull(newContext)).isSameAs(span);
  }

  private static Stream<Arguments> spanKindsAndKeys() {
    return Stream.of(
        Arguments.of(SpanKind.SERVER, SpanKey.KIND_SERVER),
        Arguments.of(SpanKind.CLIENT, SpanKey.KIND_CLIENT),
        Arguments.of(SpanKind.CONSUMER, SpanKey.KIND_CONSUMER),
        Arguments.of(SpanKind.PRODUCER, SpanKey.KIND_PRODUCER));
  }

  @Test
  void semconv_shouldNotSuppressAnythingWhenThereAreNoSpanKeys() {
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(emptySet());
    Context context = Context.root();

    assertThat(suppressor.shouldSuppress(context, SpanKind.SERVER)).isFalse();

    Context newContext = suppressor.storeInContext(context, SpanKind.SERVER, span);
    assertThat(context).isSameAs(newContext);
  }

  @Test
  void semconv_shouldStoreProvidedSpanKeysInContext() {
    Set<SpanKey> spanKeys = new HashSet<>(asList(SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT));
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(spanKeys);
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, SpanKind.SERVER, span);
    assertThat(context).isNotSameAs(newContext);

    spanKeys.forEach(key -> assertThat(key.fromContextOrNull(newContext)).isSameAs(span));
  }

  @Test
  void semconv_shouldSuppressContextWhenAllSpanKeysArePresent() {
    Set<SpanKey> spanKeys = new HashSet<>(asList(SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT));
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(spanKeys);

    Context context =
        SpanKey.RPC_CLIENT.storeInContext(
            SpanKey.DB_CLIENT.storeInContext(Context.root(), span), span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.SERVER)).isTrue();
  }

  @Test
  void semconv_shouldNotSuppressContextWithPartiallyDifferentSpanKeys() {
    Set<SpanKey> spanKeys = new HashSet<>(asList(SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT));
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(spanKeys);

    Context context =
        SpanKey.HTTP_CLIENT.storeInContext(
            SpanKey.DB_CLIENT.storeInContext(Context.root(), span), span);

    assertThat(suppressor.shouldSuppress(context, SpanKind.SERVER)).isFalse();
  }

  @Test
  void context_shouldSuppressWhenKeyIsAvailableAndTrue() {
    InstrumentationUtil.suppressInstrumentation(
        () -> {
          SpanSuppressor suppressor =
              new SpanSuppressors.ByContextKey(SpanSuppressionStrategy.NONE.create(emptySet()));

          assertThat(suppressor.shouldSuppress(Context.current(), SpanKind.CLIENT)).isTrue();
        });
  }

  @Test
  void context_shouldNotSuppressWhenKeyIsNotAvailable() {
    Context context = Context.current();
    SpanSuppressor suppressor =
        new SpanSuppressors.ByContextKey(SpanSuppressionStrategy.NONE.create(emptySet()));

    assertThat(suppressor.shouldSuppress(context, SpanKind.CLIENT)).isFalse();
  }
}
