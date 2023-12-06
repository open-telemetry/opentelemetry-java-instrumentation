/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;

public final class RedissonInstrumenterFactory {

  public static Instrumenter<RedissonRequest, Void> createInstrumenter(String instrumentationName) {
    RedissonDbAttributeGetter dbAttributeGetter = new RedissonDbAttributeGetter();
    RedissonNetAttributeGetter netAttributeGetter = new RedissonNetAttributeGetter();

    return Instrumenter.<RedissonRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbAttributeGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributeGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributeGetter))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private RedissonInstrumenterFactory() {}
}
