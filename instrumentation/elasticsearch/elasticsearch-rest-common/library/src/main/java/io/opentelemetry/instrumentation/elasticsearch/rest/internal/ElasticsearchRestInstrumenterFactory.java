/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.List;
import java.util.Set;
import org.elasticsearch.client.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ElasticsearchRestInstrumenterFactory {

  private ElasticsearchRestInstrumenterFactory() {}

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      List<AttributesExtractor<ElasticsearchRestRequest, Response>> attributesExtractors,
      Set<String> knownMethods,
      boolean captureSearchQuery) {
    ElasticsearchDbAttributesGetter dbClientAttributesGetter =
        new ElasticsearchDbAttributesGetter(captureSearchQuery);
    ElasticsearchClientAttributeExtractor esClientAtrributesExtractor =
        new ElasticsearchClientAttributeExtractor(knownMethods);
    ElasticsearchSpanNameExtractor nameExtractor =
        new ElasticsearchSpanNameExtractor(dbClientAttributesGetter);

    return Instrumenter.<ElasticsearchRestRequest, Response>builder(
            openTelemetry, instrumentationName, nameExtractor)
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(esClientAtrributesExtractor)
        .addAttributesExtractors(attributesExtractors)
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
