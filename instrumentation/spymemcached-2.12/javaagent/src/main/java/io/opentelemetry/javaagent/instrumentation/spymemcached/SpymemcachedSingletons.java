/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class SpymemcachedSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spymemcached-2.12";

  private static final Instrumenter<SpymemcachedRequest, Object> INSTRUMENTER;

  static {
    SpymemcachedAttributeGetter dbAttributeGetter = new SpymemcachedAttributeGetter();

    INSTRUMENTER =
        Instrumenter.builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributeGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributeGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<SpymemcachedRequest, Object> instrumenter() {
    return INSTRUMENTER;
  }

  private SpymemcachedSingletons() {}
}
