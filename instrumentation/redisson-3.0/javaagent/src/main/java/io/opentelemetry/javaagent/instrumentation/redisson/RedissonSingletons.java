/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class RedissonSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-3.0";

  private static final Instrumenter<RedissonRequest, Void> INSTRUMENTER;

  static {
    RedissonDbAttributesGetter dbAttributesGetter = new RedissonDbAttributesGetter();
    RedissonNetAttributesGetter netAttributesGetter = new RedissonNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<RedissonRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RedissonRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RedissonSingletons() {}
}
