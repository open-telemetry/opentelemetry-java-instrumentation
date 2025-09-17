/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;

public final class OpenSearchJavaInstrumenterFactory {

  public static Instrumenter<OpenSearchJavaRequest, OpenSearchJavaResponse> create(
      String instrumentationName) {
    OpenSearchJavaAttributesGetter dbClientAttributesGetter = new OpenSearchJavaAttributesGetter();
    OpenSearchJavaNetResponseAttributesGetter netAttributesGetter =
        new OpenSearchJavaNetResponseAttributesGetter();

    return Instrumenter.<OpenSearchJavaRequest, OpenSearchJavaResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchJavaInstrumenterFactory() {}
}
