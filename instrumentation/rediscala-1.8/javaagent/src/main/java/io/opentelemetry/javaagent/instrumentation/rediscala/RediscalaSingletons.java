/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import redis.RedisCommand;

public final class RediscalaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rediscala-1.8";

  private static final Instrumenter<RedisCommand<?, ?>, Void> INSTRUMENTER;

  static {
    RediscalaAttributeGetter dbAttributeGetter = new RediscalaAttributeGetter();

    INSTRUMENTER =
        Instrumenter.<RedisCommand<?, ?>, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributeGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributeGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RedisCommand<?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RediscalaSingletons() {}
}
