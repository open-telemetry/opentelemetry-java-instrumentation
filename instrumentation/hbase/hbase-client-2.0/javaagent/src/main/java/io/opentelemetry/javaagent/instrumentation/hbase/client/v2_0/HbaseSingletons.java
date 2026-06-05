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
import javax.annotation.Nullable;

public class HbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0";
  private static final ThreadLocal<String> tableNameThreadLocal = new ThreadLocal<>();
  private static final ThreadLocal<RequestAndContext> requestAndContextThreadLocal =
      new ThreadLocal<>();
  private static final Instrumenter<HbaseRequest, Void> instrumenter = createInstrumenter();

  public static void setTableName(String tableName) {
    tableNameThreadLocal.set(tableName);
  }

  @Nullable
  public static String getTableName() {
    return tableNameThreadLocal.get();
  }

  public static void resetTableName() {
    tableNameThreadLocal.remove();
  }

  public static void setRequestAndContext(RequestAndContext requestAndContext) {
    requestAndContextThreadLocal.set(requestAndContext);
  }

  @Nullable
  public static RequestAndContext getRequestAndContext() {
    return requestAndContextThreadLocal.get();
  }

  public static void resetRequestAndContext() {
    requestAndContextThreadLocal.remove();
  }

  public static Instrumenter<HbaseRequest, Void> instrumenter() {
    return instrumenter;
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
