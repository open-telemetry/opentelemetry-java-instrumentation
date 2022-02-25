/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class CouchbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.couchbase-2.0";

  private static final Instrumenter<CouchbaseRequestInfo, Void> INSTRUMENTER;

  static {
    CouchbaseAttributesExtractor couchbaseAttributesExtractor = new CouchbaseAttributesExtractor();
    SpanNameExtractor<CouchbaseRequestInfo> spanNameExtractor =
        new CouchbaseSpanNameExtractor(DbSpanNameExtractor.create(couchbaseAttributesExtractor));
    CouchbaseNetAttributesGetter netAttributesGetter = new CouchbaseNetAttributesGetter();
    NetClientAttributesExtractor<CouchbaseRequestInfo, Void> netClientAttributesExtractor =
        NetClientAttributesExtractor.create(netAttributesGetter);

    InstrumenterBuilder<CouchbaseRequestInfo, Void> builder =
        Instrumenter.<CouchbaseRequestInfo, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(couchbaseAttributesExtractor)
            .addAttributesExtractor(netClientAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addContextCustomizer(
                (context, couchbaseRequest, startAttributes) ->
                    CouchbaseRequestInfoHolder.init(context, couchbaseRequest));

    if (Config.get()
        .getBoolean("otel.instrumentation.couchbase.experimental-span-attributes", false)) {
      builder.addAttributesExtractor(new ExperimentalAttributesExtractor());
    }

    INSTRUMENTER = builder.newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<CouchbaseRequestInfo, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CouchbaseSingletons() {}
}
