/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class LettuceSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-5.0";

  private static final Instrumenter<RedisCommand<?, ?, ?>, Void> INSTRUMENTER;
  private static final Instrumenter<RedisURI, Void> CONNECT_INSTRUMENTER;

  public static final ContextKey<Context> COMMAND_CONTEXT_KEY =
      ContextKey.named("opentelemetry-lettuce-v5_0-context-key");

  static {
    DbAttributesExtractor<RedisCommand<?, ?, ?>, Void> attributesExtractor =
        new LettuceDbAttributesExtractor();
    SpanNameExtractor<RedisCommand<?, ?, ?>> spanName =
        DbSpanNameExtractor.create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.<RedisCommand<?, ?, ?>, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .newInstrumenter(SpanKindExtractor.alwaysClient());

    LettuceConnectNetAttributesAdapter connectNetAttributesAdapter =
        new LettuceConnectNetAttributesAdapter();
    NetClientAttributesExtractor<RedisURI, Void> netAttributesExtractor =
        NetClientAttributesExtractor.create(connectNetAttributesAdapter);
    CONNECT_INSTRUMENTER =
        Instrumenter.<RedisURI, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, redisUri -> "CONNECT")
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(connectNetAttributesAdapter))
            .addAttributesExtractor(new LettuceConnectAttributesExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RedisCommand<?, ?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<RedisURI, Void> connectInstrumenter() {
    return CONNECT_INSTRUMENTER;
  }

  private LettuceSingletons() {}
}
