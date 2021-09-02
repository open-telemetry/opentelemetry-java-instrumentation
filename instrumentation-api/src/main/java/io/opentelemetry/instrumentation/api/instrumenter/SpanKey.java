/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Makes span keys for specific instrumentation accessible to enrich and suppress spans. */
public final class SpanKey {

  private static final ContextKey<Span> SERVER_KEY =
      ContextKey.named("opentelemetry-traces-span-key-server");
  private static final ContextKey<Span> CONSUMER_KEY =
      ContextKey.named("opentelemetry-traces-span-key-consumer");

  private static final ContextKey<Span> HTTP_KEY =
      ContextKey.named("opentelemetry-traces-span-key-http");
  private static final ContextKey<Span> RPC_KEY =
      ContextKey.named("opentelemetry-traces-span-key-rpc");
  private static final ContextKey<Span> DB_KEY =
      ContextKey.named("opentelemetry-traces-span-key-db");
  private static final ContextKey<Span> MESSAGING_KEY =
      ContextKey.named("opentelemetry-traces-span-key-messaging");

  // this is used instead of above, depending on the configuration value for
  // otel.instrumentation.experimental.outgoing-span-suppression-by-type
  private static final ContextKey<Span> CLIENT_KEY =
      ContextKey.named("opentelemetry-traces-span-key-client");

  private static final ContextKey<Span> PRODUCER_KEY =
      ContextKey.named("opentelemetry-traces-span-key-producer");

  public static final SpanKey SERVER = new SpanKey(SERVER_KEY);
  public static final SpanKey CONSUMER = new SpanKey(CONSUMER_KEY);

  static final SpanKey HTTP_CLIENT = new SpanKey(HTTP_KEY);
  static final SpanKey RPC_CLIENT = new SpanKey(RPC_KEY);
  static final SpanKey DB_CLIENT = new SpanKey(DB_KEY);
  static final SpanKey MESSAGING_PRODUCER = new SpanKey(MESSAGING_KEY);

  // this is used instead of above, depending on the configuration value for
  // otel.instrumentation.experimental.outgoing-span-suppression-by-type
  public static final SpanKey ALL_CLIENTS = new SpanKey(CLIENT_KEY);
  public static final SpanKey ALL_PRODUCERS = new SpanKey(PRODUCER_KEY);

  private final ContextKey<Span> key;

  SpanKey(ContextKey<Span> key) {
    this.key = key;
  }

  public Context storeInContext(Context context, Span span) {
    return context.with(key, span);
  }

  @Nullable
  public Span fromContextOrNull(Context context) {
    return context.get(key);
  }
}
