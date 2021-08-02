/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SpanSuppressionStrategyTest {

  private static final Span SPAN = Span.getInvalid();

  @Test
  public void serverSpan_getSet() {
    assertThat(SpanKey.SERVER.fromContextOrNull(Context.root())).isNull();

    Context context =
        SpanKey.SERVER.with(Context.root(), SPAN);

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Arrays.asList(SpanKey.SERVER));
    assertThat(strategy.shouldSuppress(SpanKind.SERVER, context)).isTrue();

    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isSameAs(SPAN);
    allClientSpanKeys()
        .forEach(spanKey -> assertThat(spanKey.fromContextOrNull(context)).isNull());

    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isNull();
  }

  @Test
  public void serverSpan_getSetWithStrategy() {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Arrays.asList(SpanKey.SERVER));
    assertThat(strategy.shouldSuppress(SpanKind.SERVER, Context.root())).isFalse();
    assertThat(SpanKey.SERVER.fromContextOrNull(Context.root())).isNull();

    Context context = strategy.storeInContext(SpanKind.SERVER, Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(SpanKind.SERVER, context)).isTrue();
    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys()
        .forEach(spanKey -> assertThat(spanKey.fromContextOrNull(context)).isNull());

    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isNull();
  }

  @Test
  public void consumerSpan_getSet() {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Arrays.asList(SpanKey.CONSUMER));

    assertThat(strategy.shouldSuppress(SpanKind.CONSUMER, Context.root())).isFalse();
    assertThat(SpanKey.CONSUMER.fromContextOrNull(Context.root())).isNull();

    Context context =
        SpanKey.CONSUMER.with(Context.root(), SPAN);

    // never suppress CONSUMER
    assertThat(strategy.shouldSuppress(SpanKind.CONSUMER, context)).isFalse();

    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isSameAs(SPAN);
    allClientSpanKeys()
        .forEach(spanKey -> assertThat(spanKey.fromContextOrNull(context)).isNull());

    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isNull();
  }

  @ParameterizedTest
  @MethodSource("allClientSpanKeys")
  public void clientSpan_differentForAllTypes(SpanKey spanKey) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Arrays.asList(spanKey));
    assertThat(strategy.shouldSuppress(SpanKind.CLIENT, Context.root())).isFalse();

    Context context =
        spanKey.with(Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(SpanKind.CLIENT, context)).isTrue();
    assertThat(strategy.shouldSuppress(SpanKind.SERVER, context)).isFalse();
    assertThat(strategy.shouldSuppress(SpanKind.CONSUMER, context)).isFalse();

    assertThat(spanKey.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys()
        .filter(key -> key != spanKey)
        .forEach(
            key -> assertThat(key.fromContextOrNull(context)).isNull());
  }

  @ParameterizedTest
  @MethodSource("allClientSpanKeys")
  public void client_sameAsProducer(SpanKey spanKey) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Arrays.asList(spanKey));

    Context context =
        spanKey.with(Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(SpanKind.CLIENT, context)).isTrue();
    assertThat(strategy.shouldSuppress(SpanKind.PRODUCER, context)).isTrue();
  }

  @Test
  public void multipleKeys() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(
        allClientSpanKeys().collect(Collectors.toList()));

    Context context = strategy.storeInContext(SpanKind.CLIENT, Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(SpanKind.CLIENT, context)).isTrue();
    assertThat(strategy.shouldSuppress(SpanKind.PRODUCER, context)).isTrue();
    assertThat(strategy.shouldSuppress(SpanKind.SERVER, context)).isFalse();
    assertThat(strategy.shouldSuppress(SpanKind.CONSUMER, context)).isFalse();

    allClientSpanKeys().forEach(key -> {
      assertThat(key.fromContextOrNull(context)).isSameAs(SPAN);
    });
  }

  @Test
  public void noKeys_clientIsNeverSuppressed() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(new ArrayList<>());

    Context context = strategy.storeInContext(SpanKind.CLIENT, Context.root(), SPAN);
    assertThat(context).isSameAs(Context.root());

    assertThat(strategy.shouldSuppress(SpanKind.CLIENT, context)).isFalse();
    assertThat(strategy.shouldSuppress(SpanKind.PRODUCER, context)).isFalse();
    assertThat(strategy.shouldSuppress(SpanKind.SERVER, context)).isFalse();
    assertThat(strategy.shouldSuppress(SpanKind.CONSUMER, context)).isFalse();

    allClientSpanKeys().forEach(key -> {
      assertThat(key.fromContextOrNull(context)).isNull();
    });
  }

  @Test
  public void noKeys_serverIsSuppressed() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(new ArrayList<>());

    Context context = strategy.storeInContext(SpanKind.SERVER, Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(SpanKind.SERVER, context)).isTrue();
    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys().forEach(key -> {
      assertThat(key.fromContextOrNull(context)).isNull();
    });
  }

  @Test
  public void noKeys_consumerIsNeverSuppressed() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(new ArrayList<>());

    Context context = strategy.storeInContext(SpanKind.CONSUMER, Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(SpanKind.CONSUMER, context)).isFalse();
    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys().forEach(key -> {
      assertThat(key.fromContextOrNull(context)).isNull();
    });
  }

  private static Stream<SpanKey> allClientSpanKeys() {
    return Stream.of(
        SpanKey.OUTGOING,
        SpanKey.HTTP,
        SpanKey.DB,
        SpanKey.RPC,
        SpanKey.MESSAGING);
  }
}
