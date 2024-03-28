/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.metrics.AerospikeMetrics;

public final class AersopikeSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aerospike-client-7.0";

  private static final Instrumenter<AerospikeRequest, Void> INSTRUMENTER;

  static {
    AerospikeDbAttributesGetter aerospikeDbAttributesGetter = new AerospikeDbAttributesGetter();
    NetworkAttributesGetter<AerospikeRequest, Void> netAttributesGetter =
        new AerospikeNetworkAttributesGetter();

    InstrumenterBuilder<AerospikeRequest, Void> builder =
        Instrumenter.<AerospikeRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(aerospikeDbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(aerospikeDbAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter));
    InstrumentationConfig instrumentationConfig = InstrumentationConfig.get();
    if (instrumentationConfig.getBoolean(
        "otel.instrumentation.aerospike.experimental-span-attributes", false)) {
      builder.addAttributesExtractor(new AerospikeClientAttributeExtractor());
    }
    if (instrumentationConfig.getBoolean(
        "otel.instrumentation.aerospike.experimental-metrics", false)) {
      builder.addOperationMetrics(AerospikeMetrics.get());
    }

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<AerospikeRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private AersopikeSingletons() {}
}
