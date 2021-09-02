/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.HashSet;
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

    Context context = SpanKey.SERVER.storeInContext(Context.root(), SPAN);

    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.from(Collections.singleton(SpanKey.SERVER));
    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isTrue();

    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isSameAs(SPAN);
    allClientSpanKeys().forEach(spanKey -> assertThat(spanKey.fromContextOrNull(context)).isNull());

    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isNull();
  }

  @Test
  public void serverSpan_getSetWithStrategy() {
    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.from(Collections.singleton(SpanKey.SERVER));
    assertThat(strategy.shouldSuppress(Context.root(), SpanKind.SERVER)).isFalse();
    assertThat(SpanKey.SERVER.fromContextOrNull(Context.root())).isNull();

    Context context = strategy.storeInContext(Context.root(), SpanKind.SERVER, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isTrue();
    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys().forEach(spanKey -> assertThat(spanKey.fromContextOrNull(context)).isNull());

    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isNull();
  }

  @Test
  public void consumerSpan_getSet() {
    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.from(Collections.singleton(SpanKey.CONSUMER));

    assertThat(strategy.shouldSuppress(Context.root(), SpanKind.CONSUMER)).isFalse();
    assertThat(SpanKey.CONSUMER.fromContextOrNull(Context.root())).isNull();

    Context context = SpanKey.CONSUMER.storeInContext(Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CONSUMER)).isTrue();

    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isSameAs(SPAN);
    allClientSpanKeys().forEach(spanKey -> assertThat(spanKey.fromContextOrNull(context)).isNull());

    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isNull();
  }

  @ParameterizedTest
  @MethodSource("allClientSpanKeys")
  public void clientSpan_differentForAllTypes(SpanKey spanKey) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Collections.singleton(spanKey));
    assertThat(strategy.shouldSuppress(Context.root(), SpanKind.CLIENT)).isFalse();

    Context context = spanKey.storeInContext(Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    assertThat(strategy.shouldSuppress(context, SpanKind.CONSUMER)).isFalse();

    assertThat(spanKey.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys()
        .filter(key -> key != spanKey)
        .forEach(key -> assertThat(key.fromContextOrNull(context)).isNull());
  }

  @ParameterizedTest
  @MethodSource("allClientSpanKeys")
  public void client_sameAsProducer(SpanKey spanKey) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(Collections.singleton(spanKey));

    Context context = spanKey.storeInContext(Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
    assertThat(strategy.shouldSuppress(context, SpanKind.PRODUCER)).isTrue();

    allClientSpanKeys()
        .forEach(
            anotherKey -> {
              if (spanKey != anotherKey) {
                SpanSuppressionStrategy anotherStrategy =
                    SpanSuppressionStrategy.from(Collections.singleton(anotherKey));
                assertThat(anotherStrategy.shouldSuppress(context, SpanKind.CLIENT)).isFalse();
                assertThat(anotherStrategy.shouldSuppress(context, SpanKind.PRODUCER)).isFalse();
              }
            });
  }

  @Test
  public void allNestedOutgoing_producerDoesNotSuppressClient() {
    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.SUPPRESS_ALL_NESTED_OUTGOING_STRATEGY;

    Context contextClient = SpanKey.ALL_CLIENTS.storeInContext(Context.root(), SPAN);
    Context contextProducer = SpanKey.ALL_PRODUCERS.storeInContext(Context.root(), SPAN);

    assertThat(strategy.shouldSuppress(contextClient, SpanKind.CLIENT)).isTrue();
    assertThat(strategy.shouldSuppress(contextClient, SpanKind.PRODUCER)).isFalse();

    assertThat(strategy.shouldSuppress(contextProducer, SpanKind.CLIENT)).isFalse();
    assertThat(strategy.shouldSuppress(contextProducer, SpanKind.PRODUCER)).isTrue();
  }

  @Test
  public void multipleKeys() {

    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.from(allClientSpanKeys().collect(Collectors.toSet()));

    Context context = strategy.storeInContext(Context.root(), SpanKind.CLIENT, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
    assertThat(strategy.shouldSuppress(context, SpanKind.PRODUCER)).isTrue();
    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    assertThat(strategy.shouldSuppress(context, SpanKind.CONSUMER)).isFalse();

    allClientSpanKeys()
        .forEach(
            key -> {
              assertThat(key.fromContextOrNull(context)).isSameAs(SPAN);
            });
  }

  @Test
  public void noKeys_clientIsNeverSuppressed() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(new HashSet<>());

    Context context = strategy.storeInContext(Context.root(), SpanKind.CLIENT, SPAN);
    assertThat(context).isSameAs(Context.root());

    assertThat(strategy.shouldSuppress(context, SpanKind.CLIENT)).isFalse();
    assertThat(strategy.shouldSuppress(context, SpanKind.PRODUCER)).isFalse();
    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    assertThat(strategy.shouldSuppress(context, SpanKind.CONSUMER)).isFalse();

    allClientSpanKeys()
        .forEach(
            key -> {
              assertThat(key.fromContextOrNull(context)).isNull();
            });
  }

  @Test
  public void noKeys_serverIsSuppressed() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(new HashSet<>());

    Context context = strategy.storeInContext(Context.root(), SpanKind.SERVER, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isTrue();
    assertThat(SpanKey.SERVER.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys()
        .forEach(
            key -> {
              assertThat(key.fromContextOrNull(context)).isNull();
            });
  }

  @Test
  public void noKeys_consumerIsSuppressed() {

    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(new HashSet<>());

    Context context = strategy.storeInContext(Context.root(), SpanKind.CONSUMER, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CONSUMER)).isTrue();
    assertThat(SpanKey.CONSUMER.fromContextOrNull(context)).isSameAs(SPAN);

    allClientSpanKeys()
        .forEach(
            key -> {
              assertThat(key.fromContextOrNull(context)).isNull();
            });
  }

  private static Stream<SpanKey> allClientSpanKeys() {
    return Stream.of(
        SpanKey.ALL_CLIENTS,
        SpanKey.ALL_PRODUCERS,
        SpanKey.HTTP_CLIENT,
        SpanKey.DB_CLIENT,
        SpanKey.RPC_CLIENT,
        SpanKey.MESSAGING_PRODUCER);
  }
}
