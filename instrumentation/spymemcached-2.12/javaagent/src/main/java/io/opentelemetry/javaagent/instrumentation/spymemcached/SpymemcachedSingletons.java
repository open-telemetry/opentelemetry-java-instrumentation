/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;

public final class SpymemcachedSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spymemcached-2.12";

  private static final Instrumenter<SpymemcachedRequest, Object> INSTRUMENTER;

  static {
    DbAttributesExtractor<SpymemcachedRequest, Object> attributesExtractor =
        new SpymemcachedAttributeExtractor();
    SpanNameExtractor<SpymemcachedRequest> spanName = DbSpanNameExtractor
        .create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<SpymemcachedRequest, Object> instrumenter() {
    return INSTRUMENTER;
  }

  private SpymemcachedSingletons() {}
}
