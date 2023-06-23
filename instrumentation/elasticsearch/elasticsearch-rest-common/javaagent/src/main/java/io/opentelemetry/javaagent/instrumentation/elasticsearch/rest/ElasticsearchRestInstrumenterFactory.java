/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestInstrumenterFactory {

  private ElasticsearchRestInstrumenterFactory() {}

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      String instrumentationName) {
    ElasticsearchDbAttributesGetter dbClientAttributesGetter =
        new ElasticsearchDbAttributesGetter();
    ElasticsearchClientAttributeExtractor esClientAtrributesExtractor =
        new ElasticsearchClientAttributeExtractor();
    ElasticsearchSpanNameExtractor nameExtractor =
        new ElasticsearchSpanNameExtractor(dbClientAttributesGetter);

    return Instrumenter.<ElasticsearchRestRequest, Response>builder(
            GlobalOpenTelemetry.get(), instrumentationName, nameExtractor)
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(esClientAtrributesExtractor)
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }
}
