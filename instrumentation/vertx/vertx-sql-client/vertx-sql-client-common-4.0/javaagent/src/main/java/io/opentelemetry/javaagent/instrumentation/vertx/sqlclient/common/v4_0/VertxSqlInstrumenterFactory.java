/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
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

  private static final SpanNameExtractor<VertxSqlClientRequest> spanNameExtractor =
      DbClientSpanNameExtractor.create(new VertxSqlClientAttributesGetter());

  public static Instrumenter<VertxSqlClientRequest, Void> createInstrumenter(
      String instrumentationName) {
    VertxSqlClientAttributesGetter attributesGetter = new VertxSqlClientAttributesGetter();

    InstrumenterBuilder<VertxSqlClientRequest, Void> builder =
        Instrumenter.<VertxSqlClientRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(attributesGetter)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(
                            GlobalOpenTelemetry.get(), "vertx_sql_client"))
                    .build())
            .addAttributesExtractor(new VertxSqlClientLateAttributesExtractor(attributesGetter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(attributesGetter, GlobalOpenTelemetry.get()))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static void updateSpanName(Context context, VertxSqlClientRequest request) {
    Span.fromContext(context).updateName(spanNameExtractor.extract(request));
  }

  private VertxSqlInstrumenterFactory() {}
}
