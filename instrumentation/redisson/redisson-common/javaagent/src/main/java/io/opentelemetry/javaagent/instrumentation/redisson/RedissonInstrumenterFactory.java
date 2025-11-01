/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class RedissonInstrumenterFactory {

  public static Instrumenter<RedissonRequest, Void> createInstrumenter(String instrumentationName) {
    RedissonDbAttributesGetter dbAttributesGetter = new RedissonDbAttributesGetter();

    return Instrumenter.<RedissonRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private RedissonInstrumenterFactory() {}
}
