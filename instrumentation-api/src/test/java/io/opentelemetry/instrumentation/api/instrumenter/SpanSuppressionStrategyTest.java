/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

class SpanSuppressionStrategyTest {

  static final Span span = Span.getInvalid();

  @ParameterizedTest
  @ArgumentsSource(ConfigArgs.class)
  void shouldParseConfig(String value, SpanSuppressionStrategy expectedStrategy) {
    Config config =
        Config.builder()
            .addProperty("otel.instrumentation.experimental.span-suppression-strategy", value)
            .build();
    assertEquals(expectedStrategy, SpanSuppressionStrategy.fromConfig(config));
  }

  static final class ConfigArgs implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
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
  }

  @ParameterizedTest
  @ArgumentsSource(SpanKindsAndKeys.class)
  void none_shouldNotSuppressAnything(SpanKind spanKind, SpanKey spanKey) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.NONE.create(emptySet());

    Context context = spanKey.storeInContext(Context.root(), span);

    assertFalse(suppressor.shouldSuppress(context, spanKind));
  }

  @ParameterizedTest
  @EnumSource(value = SpanKind.class)
  void none_shouldNotStoreSpansInContext(SpanKind spanKind) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.NONE.create(emptySet());
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, spanKind, span);

    assertSame(newContext, context);
  }

  @ParameterizedTest
  @ArgumentsSource(SpanKindsAndKeys.class)
  void spanKind_shouldStoreInContext(SpanKind spanKind, SpanKey spanKey) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.SPAN_KIND.create(emptySet());
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, spanKind, span);

    assertNotSame(newContext, context);
    assertSame(span, spanKey.fromContextOrNull(newContext));
  }

  @ParameterizedTest
  @ArgumentsSource(SpanKindsAndKeys.class)
  void spanKind_shouldSuppressSameKind(SpanKind spanKind, SpanKey spanKey) {
    SpanSuppressor suppressor = SpanSuppressionStrategy.SPAN_KIND.create(emptySet());
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, spanKind, span);

    assertNotSame(newContext, context);
    assertSame(span, spanKey.fromContextOrNull(newContext));
  }

  static final class SpanKindsAndKeys implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(SpanKind.SERVER, SpanKey.KIND_SERVER),
          Arguments.of(SpanKind.CLIENT, SpanKey.KIND_CLIENT),
          Arguments.of(SpanKind.CONSUMER, SpanKey.KIND_CONSUMER),
          Arguments.of(SpanKind.PRODUCER, SpanKey.KIND_PRODUCER));
    }
  }

  @Test
  void semconv_shouldNotSuppressAnythingWhenThereAreNoSpanKeys() {
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(emptySet());
    Context context = Context.root();

    assertFalse(suppressor.shouldSuppress(context, SpanKind.SERVER));

    Context newContext = suppressor.storeInContext(context, SpanKind.SERVER, span);
    assertSame(newContext, context);
  }

  @Test
  void semconv_shouldStoreProvidedSpanKeysInContext() {
    Set<SpanKey> spanKeys = new HashSet<>(asList(SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT));
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(spanKeys);
    Context context = Context.root();

    Context newContext = suppressor.storeInContext(context, SpanKind.SERVER, span);
    assertNotSame(newContext, context);

    spanKeys.forEach(key -> assertSame(span, key.fromContextOrNull(newContext)));
  }

  @Test
  void semconv_shouldSuppressContextWhenAllSpanKeysArePresent() {
    Set<SpanKey> spanKeys = new HashSet<>(asList(SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT));
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(spanKeys);

    Context context =
        SpanKey.RPC_CLIENT.storeInContext(
            SpanKey.DB_CLIENT.storeInContext(Context.root(), span), span);

    assertTrue(suppressor.shouldSuppress(context, SpanKind.SERVER));
  }

  @Test
  void semconv_shouldNotSuppressContextWithPartiallyDifferentSpanKeys() {
    Set<SpanKey> spanKeys = new HashSet<>(asList(SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT));
    SpanSuppressor suppressor = SpanSuppressionStrategy.SEMCONV.create(spanKeys);

    Context context =
        SpanKey.HTTP_CLIENT.storeInContext(
            SpanKey.DB_CLIENT.storeInContext(Context.root(), span), span);

    assertFalse(suppressor.shouldSuppress(context, SpanKind.SERVER));
  }
}
