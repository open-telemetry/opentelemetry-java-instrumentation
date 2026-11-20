/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import retrofit2.Callback;

public class InfluxDbSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.influxdb-2.4";

  private static final Instrumenter<InfluxDbQuery, InfluxDbResponse> queryInstrumenter;
  private static final Instrumenter<InfluxDbOperation, Void> requestInstrumenter;
  public static final VirtualField<Callback<?>, InfluxDbQueryState> CALLBACK_QUERY_STATE =
      VirtualField.find(Callback.class, InfluxDbQueryState.class);

  static {
    InfluxDbQueryAttributesGetter queryAttributesGetter = new InfluxDbQueryAttributesGetter();

    InstrumenterBuilder<InfluxDbQuery, InfluxDbResponse> queryBuilder =
        Instrumenter.<InfluxDbQuery, InfluxDbResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(queryAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(queryAttributesGetter)
                    .setQuerySanitizationEnabled(
                        DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "influxdb"))
                    .build())
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .setSpanStatusExtractor(
                (status, request, response, error) -> {
                  if (response != null && response.getErrorType() != null) {
                    status.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault().extract(status, request, response, error);
                  }
                })
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(queryBuilder);

    queryInstrumenter = queryBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient());

    InfluxDbAttributesGetter dbAttributesGetter = new InfluxDbAttributesGetter();

    InstrumenterBuilder<InfluxDbOperation, Void> modifyBuilder =
        Instrumenter.<InfluxDbOperation, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(modifyBuilder);

    requestInstrumenter = modifyBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<InfluxDbQuery, InfluxDbResponse> queryInstrumenter() {
    return queryInstrumenter;
  }

  public static Instrumenter<InfluxDbOperation, Void> requestInstrumenter() {
    return requestInstrumenter;
  }

  private InfluxDbSingletons() {}
}
