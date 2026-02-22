/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;

public final class OpenSearchRestInstrumenterFactory {

  public static Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> create(
      String instrumentationName) {
    OpenSearchRestAttributesGetter dbClientAttributesGetter = new OpenSearchRestAttributesGetter();
    OpenSearchRestNetResponseAttributesGetter netAttributesGetter =
        new OpenSearchRestNetResponseAttributesGetter();

    InstrumenterBuilder<OpenSearchRestRequest, OpenSearchRestResponse> builder =
        Instrumenter.<OpenSearchRestRequest, OpenSearchRestResponse>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                DbClientSpanNameExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    Experimental.setExceptionEventExtractor(builder, DbExceptionEventExtractors.client());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchRestInstrumenterFactory() {}
}
