/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestInstrumenterFactory {

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      String instrumentationName) {
    ElasticsearchRestAttributesGetter dbClientAttributesGetter =
        new ElasticsearchRestAttributesGetter();
    ElasticsearchRestNetResponseAttributesGetter netAttributesGetter =
        new ElasticsearchRestNetResponseAttributesGetter();

    return Instrumenter.<ElasticsearchRestRequest, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractor(
            PeerServiceAttributesExtractor.create(
                netAttributesGetter, CommonConfig.get().getPeerServiceMapping()))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ElasticsearchRestInstrumenterFactory() {}
}
