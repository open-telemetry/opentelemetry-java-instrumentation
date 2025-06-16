/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class VertxSqlInstrumenterFactory {

  public static Instrumenter<VertxSqlClientRequest, Void> createInstrumenter(
      String instrumentationName) {
    SpanNameExtractor<VertxSqlClientRequest> spanNameExtractor =
        DbClientSpanNameExtractor.create(VertxSqlClientAttributesGetter.INSTANCE);

    InstrumenterBuilder<VertxSqlClientRequest, Void> builder =
        Instrumenter.<VertxSqlClientRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(VertxSqlClientAttributesGetter.INSTANCE)
                    .setStatementSanitizationEnabled(
                        AgentCommonConfig.get().isStatementSanitizationEnabled())
                    .build())
            .addAttributesExtractor(
                ServerAttributesExtractor.create(VertxSqlClientNetAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    VertxSqlClientNetAttributesGetter.INSTANCE,
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(DbClientMetrics.get());

    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private VertxSqlInstrumenterFactory() {}
}
