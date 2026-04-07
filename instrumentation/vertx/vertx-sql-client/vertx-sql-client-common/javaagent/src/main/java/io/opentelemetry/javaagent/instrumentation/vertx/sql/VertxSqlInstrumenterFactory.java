/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class VertxSqlInstrumenterFactory {

  public static Instrumenter<VertxSqlClientRequest, Void> createInstrumenter(
      String instrumentationName) {
    VertxSqlClientAttributesGetter attributesGetter = new VertxSqlClientAttributesGetter();
    SpanNameExtractor<VertxSqlClientRequest> spanNameExtractor =
        DbClientSpanNameExtractor.create(attributesGetter);

    InstrumenterBuilder<VertxSqlClientRequest, Void> builder =
        Instrumenter.<VertxSqlClientRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(attributesGetter)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(
                            GlobalOpenTelemetry.get(), "vertx_sql_client"))
                    .build())
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(attributesGetter, GlobalOpenTelemetry.get()))
            .addOperationMetrics(DbClientMetrics.get());

    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private VertxSqlInstrumenterFactory() {}
}
