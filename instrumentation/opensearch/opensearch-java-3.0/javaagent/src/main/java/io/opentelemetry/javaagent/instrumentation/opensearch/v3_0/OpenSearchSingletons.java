/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public final class OpenSearchSingletons {
  private static final Instrumenter<OpenSearchRequest, Void> INSTRUMENTER = createInstrumenter();

  public static Instrumenter<OpenSearchRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private static Instrumenter<OpenSearchRequest, Void> createInstrumenter() {
    OpenSearchAttributesGetter dbClientAttributesGetter = new OpenSearchAttributesGetter();

    return Instrumenter.<OpenSearchRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            "io.opentelemetry.opensearch-java-3.0",
            DbClientSpanNameExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchSingletons() {}
}
