/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import org.opensearch.client.Response;

public final class OpenSearchRestInstrumenterFactory {

  public static Instrumenter<OpenSearchRestRequest, Response> create(String instrumentationName) {
    OpenSearchRestAttributesGetter dbClientAttributesGetter = new OpenSearchRestAttributesGetter();
    OpenSearchRestNetResponseAttributesGetter netAttributesGetter =
        new OpenSearchRestNetResponseAttributesGetter();

    return Instrumenter.<OpenSearchRestRequest, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchRestInstrumenterFactory() {}
}
