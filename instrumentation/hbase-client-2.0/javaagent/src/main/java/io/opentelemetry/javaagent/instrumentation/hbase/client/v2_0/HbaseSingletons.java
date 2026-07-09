/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Row;

public class HbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0";
  // Batch metadata is carried in the context so it survives the hop from the Table.batch(...)
  // caller thread to the pool thread that issues the underlying Multi RPC.
  private static final ContextKey<HbaseBatchMetadata> BATCH_METADATA_KEY =
      ContextKey.named("opentelemetry-hbase-batch-metadata");
  private static final ThreadLocal<TableName> tableNameThreadLocal = new ThreadLocal<>();
  private static final ThreadLocal<RequestAndContext> requestAndContextThreadLocal =
      new ThreadLocal<>();
  private static final Instrumenter<HbaseRequest, Void> instrumenter = createInstrumenter();

  public static void setTableName(TableName tableName) {
    tableNameThreadLocal.set(tableName);
  }

  @Nullable
  public static TableName getTableName() {
    return tableNameThreadLocal.get();
  }

  public static void resetTableName() {
    tableNameThreadLocal.remove();
  }

  // Prepares a Table.batch(...) call for stable-semconv reporting. Under old semconv this is a
  // no-op (returns null) and the RPC-layer span keeps reporting the raw "Multi" operation.
  // Otherwise the derived batch metadata is placed in the context so the executor instrumentation
  // propagates it to the pool thread that issues the Multi RPC, letting that span report the batch
  // operation name and db.operation.batch.size; the returned scope must be closed when the batch
  // call returns. An empty batch issues no RPC, so its span is emitted here directly and null is
  // returned.
  @Nullable
  public static Scope startBatch(@Nullable TableName tableName, List<? extends Row> actions) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    HbaseBatchMetadata metadata = HbaseBatchMetadata.create(actions);
    Long batchSize = metadata.getOperationBatchSize();
    if (batchSize != null && batchSize == 0L) {
      startAndEndBatchSpan(tableName, metadata);
      return null;
    }
    return Context.current().with(BATCH_METADATA_KEY, metadata).makeCurrent();
  }

  @Nullable
  public static HbaseBatchMetadata getBatchMetadata(Context context) {
    return context.get(BATCH_METADATA_KEY);
  }

  // Emits a self-contained span for an empty batch. No RPC is issued, so there is no user, host or
  // port to record -- only the operation name, batch size and table namespace/collection.
  private static void startAndEndBatchSpan(
      @Nullable TableName tableName, HbaseBatchMetadata metadata) {
    HbaseRequest request =
        HbaseRequest.create(
            metadata.getOperation(), tableName, null, null, null, metadata.getOperationBatchSize());
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return;
    }
    Context context = instrumenter.start(parentContext, request);
    instrumenter.end(context, request, null, null);
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
