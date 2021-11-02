/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

/** Makes span keys for specific instrumentation accessible to enrich and suppress spans. */
public final class SpanKey {

  // server span key

  private static final ContextKey<Span> SERVER_KEY =
      ContextKey.named("opentelemetry-traces-span-key-server");

  // client span keys

  private static final ContextKey<Span> HTTP_CLIENT_KEY =
      ContextKey.named("opentelemetry-traces-span-key-http");
  private static final ContextKey<Span> RPC_CLIENT_KEY =
      ContextKey.named("opentelemetry-traces-span-key-rpc");
  private static final ContextKey<Span> DB_CLIENT_KEY =
      ContextKey.named("opentelemetry-traces-span-key-db");

  // this is used instead of above, depending on the configuration value for
  // otel.instrumentation.experimental.outgoing-span-suppression-by-type
  private static final ContextKey<Span> CLIENT_KEY =
      ContextKey.named("opentelemetry-traces-span-key-client");

  // producer & consumer (messaging) span keys

  private static final ContextKey<Span> PRODUCER_KEY =
      ContextKey.named("opentelemetry-traces-span-key-producer");
  private static final ContextKey<Span> CONSUMER_RECEIVE_KEY =
      ContextKey.named("opentelemetry-traces-span-key-consumer-receive");
  private static final ContextKey<Span> CONSUMER_PROCESS_KEY =
      ContextKey.named("opentelemetry-traces-span-key-consumer-process");

  public static final SpanKey SERVER = new SpanKey(SERVER_KEY);

  static final SpanKey HTTP_CLIENT = new SpanKey(HTTP_CLIENT_KEY);
  static final SpanKey RPC_CLIENT = new SpanKey(RPC_CLIENT_KEY);
  static final SpanKey DB_CLIENT = new SpanKey(DB_CLIENT_KEY);

  // this is used instead of above, depending on the configuration value for
  // otel.instrumentation.experimental.outgoing-span-suppression-by-type
  public static final SpanKey ALL_CLIENTS = new SpanKey(CLIENT_KEY);

  static final SpanKey PRODUCER = new SpanKey(PRODUCER_KEY);
  static final SpanKey CONSUMER_RECEIVE = new SpanKey(CONSUMER_RECEIVE_KEY);
  public static final SpanKey CONSUMER_PROCESS = new SpanKey(CONSUMER_PROCESS_KEY);

  private final ContextKey<Span> key;

  private SpanKey(ContextKey<Span> key) {
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
