/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestInstrumenterFactory {

  public static Instrumenter<String, Response> create(String instrumentationName) {
    ElasticsearchRestAttributesExtractor attributesExtractor =
        new ElasticsearchRestAttributesExtractor();
    SpanNameExtractor<String> spanNameExtractor = DbSpanNameExtractor.create(attributesExtractor);
    ElasticsearchRestNetResponseAttributesGetter netAttributesGetter =
        new ElasticsearchRestNetResponseAttributesGetter();
    NetClientAttributesExtractor<String, Response> netAttributesExtractor =
        NetClientAttributesExtractor.create(netAttributesGetter);

    return Instrumenter.<String, Response>builder(
            GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
        .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ElasticsearchRestInstrumenterFactory() {}
}
