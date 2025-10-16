/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class AerospikeSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-aerospike-client-3.9";
  private static final Instrumenter<AerospikeRequest, Void> INSTRUMENTER;

  static {
    SpanNameExtractor<AerospikeRequest> spanNameExtractor = AerospikeRequest::getOperation;

    InstrumenterBuilder<AerospikeRequest, Void> builder =
        Instrumenter.<AerospikeRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(
                DbClientAttributesExtractor.create(AerospikeAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                ServerAttributesExtractor.create(AerospikeNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                NetworkAttributesExtractor.create(AerospikeNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    AerospikeNetAttributesGetter.INSTANCE,
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get());

    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<AerospikeRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private AerospikeSingletons() {}
}

