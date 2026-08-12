/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class RedissonInstrumenterFactory {

  public static Instrumenter<RedissonRequest, Void> createInstrumenter(String instrumentationName) {
    RedissonDbAttributesGetter dbAttributesGetter = new RedissonDbAttributesGetter();

    InstrumenterBuilder<RedissonRequest, Void> builder =
        Instrumenter.<RedissonRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private RedissonInstrumenterFactory() {}
}
