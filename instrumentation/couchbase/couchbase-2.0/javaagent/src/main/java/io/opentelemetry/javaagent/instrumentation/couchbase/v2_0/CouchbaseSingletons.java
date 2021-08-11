/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;

public final class CouchbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.couchbase-2.0";

  private static final Instrumenter<CouchbaseRequest, Void> INSTRUMENTER;

  static {
    CouchbaseAttributesExtractor couchbaseAttributesExtractor = new CouchbaseAttributesExtractor();
    SpanNameExtractor<CouchbaseRequest> spanNameExtractor =
        new CouchbaseSpanNameExtractor(DbSpanNameExtractor.create(couchbaseAttributesExtractor));

    INSTRUMENTER =
        Instrumenter.<CouchbaseRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(couchbaseAttributesExtractor)
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<CouchbaseRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CouchbaseSingletons() {}
}
