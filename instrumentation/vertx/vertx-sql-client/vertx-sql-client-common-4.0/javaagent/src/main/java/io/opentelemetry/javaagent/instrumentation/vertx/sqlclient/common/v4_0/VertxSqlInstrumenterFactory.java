/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class VertxSqlInstrumenterFactory {

  public static Instrumenter<VertxSqlClientRequest, Void> createInstrumenter(
      String instrumentationName) {
    VertxSqlClientAttributesGetter attributesGetter = new VertxSqlClientAttributesGetter();

    InstrumenterBuilder<VertxSqlClientRequest, Void> builder =
        Instrumenter.<VertxSqlClientRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                DbClientSpanNameExtractor.create(attributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(attributesGetter)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(
                            GlobalOpenTelemetry.get(), "vertx_sql_client"))
                    .build())
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(attributesGetter, GlobalOpenTelemetry.get()))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private VertxSqlInstrumenterFactory() {}
}
