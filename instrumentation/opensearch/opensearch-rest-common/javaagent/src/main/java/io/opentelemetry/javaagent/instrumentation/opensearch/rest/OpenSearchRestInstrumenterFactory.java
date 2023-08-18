/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import org.opensearch.client.Response;

public final class OpenSearchRestInstrumenterFactory {

  @SuppressWarnings("deprecation") // have to use the deprecated Net*AttributesExtractor for now
  public static Instrumenter<OpenSearchRestRequest, Response> create(String instrumentationName) {
    OpenSearchRestAttributesGetter dbClientAttributesGetter = new OpenSearchRestAttributesGetter();
    OpenSearchRestNetResponseAttributesGetter netAttributesGetter =
        new OpenSearchRestNetResponseAttributesGetter();

    return Instrumenter.<OpenSearchRestRequest, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            DbClientSpanNameExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
        .addAttributesExtractor(
            io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor
                .create(netAttributesGetter))
        .addAttributesExtractor(
            PeerServiceAttributesExtractor.create(
                netAttributesGetter, CommonConfig.get().getPeerServiceResolver()))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchRestInstrumenterFactory() {}
}
