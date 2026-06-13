/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

@SuppressWarnings("deprecation") // to support old semconv
public class InfluxDbSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.influxdb-2.4";

  private static final Instrumenter<InfluxDbQuery, Void> queryInstrumenter;
  private static final Instrumenter<InfluxDbOperation, Void> requestInstrumenter;

  static {
    InfluxDbQueryAttributesGetter queryAttributesGetter = new InfluxDbQueryAttributesGetter();

    InstrumenterBuilder<InfluxDbQuery, Void> queryBuilder =
        Instrumenter.<InfluxDbQuery, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.createWithGenericOldSpanName(queryAttributesGetter))
            .addAttributesExtractor(
                SqlClientAttributesExtractor.builder(queryAttributesGetter)
                    .setTableAttribute(null)
                    .build())
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
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(modifyBuilder);

    requestInstrumenter = modifyBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<InfluxDbQuery, Void> queryInstrumenter() {
    return queryInstrumenter;
  }

  public static Instrumenter<InfluxDbOperation, Void> requestInstrumenter() {
    return requestInstrumenter;
  }

  private InfluxDbSingletons() {}
}
