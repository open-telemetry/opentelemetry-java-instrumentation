/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class CouchbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.couchbase-2.0";

  private static final Instrumenter<CouchbaseRequestInfo, Void> INSTRUMENTER;

  static {
    CouchbaseAttributeGetter couchbaseAttributeGetter = new CouchbaseAttributeGetter();
    SpanNameExtractor<CouchbaseRequestInfo> spanNameExtractor =
        new CouchbaseSpanNameExtractor(DbClientSpanNameExtractor.create(couchbaseAttributeGetter));
    CouchbaseNetworkAttributeGetter netAttributeGetter = new CouchbaseNetworkAttributeGetter();

    InstrumenterBuilder<CouchbaseRequestInfo, Void> builder =
        Instrumenter.<CouchbaseRequestInfo, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(DbClientAttributesExtractor.create(couchbaseAttributeGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributeGetter))
            .addContextCustomizer(
                (context, couchbaseRequest, startAttributes) ->
                    CouchbaseRequestInfo.init(context, couchbaseRequest));

    if (InstrumentationConfig.get()
        .getBoolean("otel.instrumentation.couchbase.experimental-span-attributes", false)) {
      builder.addAttributesExtractor(new ExperimentalAttributesExtractor());
    }

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<CouchbaseRequestInfo, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CouchbaseSingletons() {}
}
