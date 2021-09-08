/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SpanSuppressionStrategyTest {

  private static final Span SPAN = Span.getInvalid();

  @Test
  public void serverSpan() {
    // SpanKey.SERVER will never be passed to SpanSuppressionStrategy.from(), it cannot be
    // automatically determined by te builder - thus it does not make any sense to test it (for now)
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(emptySet());

    Context context = strategy.storeInContext(Context.root(), SpanKind.SERVER, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isTrue();
    Stream.of(SpanKind.CLIENT, SpanKind.CONSUMER, SpanKind.PRODUCER)
        .forEach(spanKind -> assertThat(strategy.shouldSuppress(context, spanKind)).isFalse());

    verifySpanKey(SpanKey.SERVER, context);
  }

  @ParameterizedTest
  @MethodSource("consumerSpanKeys")
  public void consumerSpan(SpanKey spanKey) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(singleton(spanKey));

    verifyNoSuppression(strategy, Context.root());

    Context context = strategy.storeInContext(Context.root(), SpanKind.CONSUMER, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    Stream.of(SpanKind.CLIENT, SpanKind.CONSUMER, SpanKind.PRODUCER)
        .forEach(spanKind -> assertThat(strategy.shouldSuppress(context, spanKind)).isTrue());

    verifySpanKey(spanKey, context);
  }

  @ParameterizedTest
  @MethodSource("clientSpanKeys")
  public void clientSpan(SpanKey spanKey) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(singleton(spanKey));

    verifyNoSuppression(strategy, Context.root());

    Context context = strategy.storeInContext(Context.root(), SpanKind.CLIENT, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    Stream.of(SpanKind.CLIENT, SpanKind.CONSUMER, SpanKind.PRODUCER)
        .forEach(spanKind -> assertThat(strategy.shouldSuppress(context, spanKind)).isTrue());

    verifySpanKey(spanKey, context);
  }

  @Test
  public void producerSpan() {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(singleton(SpanKey.PRODUCER));

    verifyNoSuppression(strategy, Context.root());

    Context context = strategy.storeInContext(Context.root(), SpanKind.PRODUCER, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    Stream.of(SpanKind.CLIENT, SpanKind.CONSUMER, SpanKind.PRODUCER)
        .forEach(spanKind -> assertThat(strategy.shouldSuppress(context, spanKind)).isTrue());

    verifySpanKey(SpanKey.PRODUCER, context);
  }

  @Test
  public void multipleClientKeys() {
    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.from(clientSpanKeys().collect(Collectors.toSet()));

    Context context = strategy.storeInContext(Context.root(), SpanKind.CLIENT, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CLIENT)).isTrue();
    assertThat(strategy.shouldSuppress(context, SpanKind.PRODUCER)).isTrue();
    assertThat(strategy.shouldSuppress(context, SpanKind.SERVER)).isFalse();
    assertThat(strategy.shouldSuppress(context, SpanKind.CONSUMER)).isTrue();

    clientSpanKeys().forEach(key -> assertThat(key.fromContextOrNull(context)).isSameAs(SPAN));
  }

  @ParameterizedTest
  @MethodSource("nonServerSpanKinds")
  public void noKeys_nonServerSpanKindsAreNotSuppressed(SpanKind spanKind) {
    SpanSuppressionStrategy strategy = SpanSuppressionStrategy.from(emptySet());

    Context context = strategy.storeInContext(Context.root(), spanKind, SPAN);

    assertThat(context).isSameAs(Context.root());
    verifyNoSuppression(strategy, context);

    allSpanKeys().forEach(key -> assertThat(key.fromContextOrNull(context)).isNull());
  }

  @Test
  public void nestedClientsDisabled_useAllClientsSpanKey() {
    SpanSuppressionStrategy strategy =
        SpanSuppressionStrategy.suppressNestedClients(allSpanKeys().collect(Collectors.toSet()));

    Context context = strategy.storeInContext(Context.root(), SpanKind.CLIENT, SPAN);

    assertThat(strategy.shouldSuppress(context, SpanKind.CLIENT)).isTrue();

    assertThat(SpanKey.ALL_CLIENTS.fromContextOrNull(context)).isSameAs(SPAN);
    allSpanKeys()
        .filter(key -> key != SpanKey.ALL_CLIENTS)
        .forEach(key -> assertThat(key.fromContextOrNull(context)).isNull());
  }

  @SuppressWarnings("unused")
  private static Stream<SpanKind> nonServerSpanKinds() {
    return Stream.of(SpanKind.CONSUMER, SpanKind.CLIENT, SpanKind.PRODUCER);
  }

  private static void verifyNoSuppression(SpanSuppressionStrategy strategy, Context context) {
    Stream.of(SpanKind.values())
        .forEach(spanKind -> assertThat(strategy.shouldSuppress(context, spanKind)).isFalse());
  }

  private static void verifySpanKey(SpanKey spanKey, Context context) {
    assertThat(spanKey.fromContextOrNull(context)).isSameAs(SPAN);
    allSpanKeys()
        .filter(key -> key != spanKey)
        .forEach(key -> assertThat(key.fromContextOrNull(context)).isNull());
  }

  private static Stream<SpanKey> allSpanKeys() {
    return Stream.concat(
        Stream.of(SpanKey.PRODUCER, SpanKey.SERVER),
        Stream.concat(consumerSpanKeys(), clientSpanKeys()));
  }

  private static Stream<SpanKey> consumerSpanKeys() {
    return Stream.of(SpanKey.CONSUMER_RECEIVE, SpanKey.CONSUMER_PROCESS);
  }

  private static Stream<SpanKey> clientSpanKeys() {
    return Stream.of(
        SpanKey.ALL_CLIENTS, SpanKey.HTTP_CLIENT, SpanKey.DB_CLIENT, SpanKey.RPC_CLIENT);
  }
}
