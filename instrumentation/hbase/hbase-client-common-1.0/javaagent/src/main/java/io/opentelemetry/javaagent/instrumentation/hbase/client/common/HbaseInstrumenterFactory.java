/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class HbaseInstrumenterFactory {

  public static Instrumenter<HbaseRequest, Void> create(String instrumentationName) {
    HbaseAttributesGetter hbaseAttributesGetter = new HbaseAttributesGetter();
    InstrumenterBuilder<HbaseRequest, Void> builder =
        Instrumenter.<HbaseRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                DbClientSpanNameExtractor.create(hbaseAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(hbaseAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private HbaseInstrumenterFactory() {}
}
