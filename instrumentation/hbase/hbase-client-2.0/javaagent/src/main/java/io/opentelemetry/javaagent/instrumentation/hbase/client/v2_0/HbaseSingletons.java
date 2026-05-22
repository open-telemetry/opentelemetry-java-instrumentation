/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

public class HbaseSingletons {

  public static final ThreadLocal<String> TABLE_THREAD_LOCAL = new ThreadLocal<>();
  public static final ThreadLocal<RequestAndContext> RC_THREAD_LOCAL = new ThreadLocal<>();

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0";
  private static final Instrumenter<HbaseRequest, Void> INSTRUMENTER = createInstrumenter();

  public static Instrumenter<HbaseRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private static Instrumenter<HbaseRequest, Void> createInstrumenter() {
    HbaseAttributesGetter hbaseAttributesGetter = new HbaseAttributesGetter();
    return Instrumenter.<HbaseRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            DbClientSpanNameExtractor.create(hbaseAttributesGetter))
        .addAttributesExtractor(DbClientAttributesExtractor.create(hbaseAttributesGetter))
        .addOperationMetrics(DbClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private HbaseSingletons() {}
}
