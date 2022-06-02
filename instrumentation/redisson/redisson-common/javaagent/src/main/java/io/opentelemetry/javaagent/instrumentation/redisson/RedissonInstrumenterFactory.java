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

public final class RedissonInstrumenterFactory {

  public static Instrumenter<RedissonRequest, Void> createInstrumenter(String instrumentationName) {
    RedissonDbAttributesGetter dbAttributesGetter = new RedissonDbAttributesGetter();
    RedissonNetAttributesGetter netAttributesGetter = new RedissonNetAttributesGetter();

    return Instrumenter.<RedissonRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private RedissonInstrumenterFactory() {}
}
