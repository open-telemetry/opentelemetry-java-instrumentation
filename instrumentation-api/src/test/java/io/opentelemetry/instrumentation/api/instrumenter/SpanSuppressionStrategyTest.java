/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.HashSet;
import java.util.Set;
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
  void shouldSetStrategyProgrammatically() {
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
      value = "span-kind")
  void shouldReadDeprecatedProperty() {
    assertThat(InstrumenterBuilder.getDeprecatedSpanSuppressionStrategyProperty())
        .isEqualTo("span-kind");
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
