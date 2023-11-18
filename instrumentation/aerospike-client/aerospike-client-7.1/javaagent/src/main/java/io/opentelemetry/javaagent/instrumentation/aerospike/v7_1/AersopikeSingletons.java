/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.aerospike.v7_1.AerospikeMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class AersopikeSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aerospike-client-7.1";

  private static final Instrumenter<AerospikeRequest, Void> INSTRUMENTER;

  static {
    DbAttributesGetter dbAttributesGetter = new DbAttributesGetter();
    NetworkAttributesGetter netAttributesGetter = new NetworkAttributesGetter();

    InstrumenterBuilder<AerospikeRequest, Void> builder =
        Instrumenter.<AerospikeRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(AerospikeMetrics.get());
    if (InstrumentationConfig.get()
        .getBoolean("otel.instrumentation.aerospike.experimental-span-attributes", false)) {
      builder.addAttributesExtractor(new AerospikeClientAttributeExtractor());
    }

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<AerospikeRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private AersopikeSingletons() {}
}
