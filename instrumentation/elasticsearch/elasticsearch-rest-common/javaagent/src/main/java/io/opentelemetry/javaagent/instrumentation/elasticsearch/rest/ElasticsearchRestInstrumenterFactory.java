/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestInstrumenterFactory {

  public static Instrumenter<String, Response> create(String instrumentationName) {
    ElasticsearchRestAttributesGetter dbClientAttributesGetter =
        new ElasticsearchRestAttributesGetter();
    ElasticsearchRestNetResponseAttributesGetter netAttributesGetter =
        new ElasticsearchRestNetResponseAttributesGetter();

    return Instrumenter.<String, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ElasticsearchRestInstrumenterFactory() {}
}
