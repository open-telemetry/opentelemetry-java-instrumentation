/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import org.opensearch.client.Response;

public final class OpenSearchRestInstrumenterFactory {

  public static Instrumenter<OpenSearchRestRequest, Response> create(String instrumentationName) {
    OpenSearchRestAttributeGetter dbClientAttributeGetter = new OpenSearchRestAttributeGetter();
    OpenSearchRestNetResponseAttributeGetter netAttributeGetter =
        new OpenSearchRestNetResponseAttributeGetter();

    return Instrumenter.<OpenSearchRestRequest, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbClientAttributeGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributeGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributeGetter))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchRestInstrumenterFactory() {}
}
