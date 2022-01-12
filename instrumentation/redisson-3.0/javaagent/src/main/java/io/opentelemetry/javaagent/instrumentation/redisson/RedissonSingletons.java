/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class RedissonSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-3.0";

  private static final Instrumenter<RedissonRequest, Void> INSTRUMENTER;

  static {
    DbAttributesExtractor<RedissonRequest, Void> dbAttributesExtractor =
        new RedissonDbAttributesExtractor();
    NetClientAttributesExtractor<RedissonRequest, Void> netAttributeExtractor =
        NetClientAttributesExtractor.create(new RedissonNetAttributesGetter());
    SpanNameExtractor<RedissonRequest> spanName = DbSpanNameExtractor.create(dbAttributesExtractor);

    INSTRUMENTER =
        Instrumenter.<RedissonRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(dbAttributesExtractor)
            .addAttributesExtractor(netAttributeExtractor)
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RedissonRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RedissonSingletons() {}
}
