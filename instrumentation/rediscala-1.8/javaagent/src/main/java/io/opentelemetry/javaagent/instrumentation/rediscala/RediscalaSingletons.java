/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import redis.RedisCommand;

public final class RediscalaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rediscala-1.8";

  private static final Instrumenter<RedisCommand<?, ?>, Void> INSTRUMENTER;

  static {
    RediscalaAttributesExtractor attributesExtractor = new RediscalaAttributesExtractor();
    SpanNameExtractor<RedisCommand<?, ?>> spanNameExtractor =
        DbSpanNameExtractor.create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.<RedisCommand<?, ?>, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RedisCommand<?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RediscalaSingletons() {}
}
