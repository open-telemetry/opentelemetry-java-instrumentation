/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import com.google.auto.value.AutoValue;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.RowMutations;

@AutoValue
public abstract class HbaseBatchMetadata {

  // HBase RPC operation names, matching the casing HBase itself reports for single operations.
  private static final String GET = "Get";
  private static final String MUTATE = "Mutate";
  private static final String MULTI = "Multi";
  private static final String BATCH = "BATCH";

  // Derives the stable-semconv operation name and batch size for a call to Table.batch(...).
  public static HbaseBatchMetadata create(List<? extends Row> actions) {
    int size = actions.size();
    if (size == 0) {
      // an empty batch request is still a batch operation with size 0
      return new AutoValue_HbaseBatchMetadata(BATCH, 0L);
    }

    String common = actionOperation(actions.get(0));
    boolean homogeneous = common != null;
    for (int i = 1; homogeneous && i < size; i++) {
      if (!common.equals(actionOperation(actions.get(i)))) {
        homogeneous = false;
      }
    }

    if (size == 1) {
      // a single operation is modeled as a non-batch operation (no db.operation.batch.size)
      return new AutoValue_HbaseBatchMetadata(common != null ? common : MULTI, null);
    }
    if (homogeneous) {
      return new AutoValue_HbaseBatchMetadata(BATCH + " " + common, (long) size);
    }
    return new AutoValue_HbaseBatchMetadata(BATCH, (long) size);
  }

  @Nullable
  private static String actionOperation(Row action) {
    if (action instanceof Get) {
      return GET;
    }
    // Put, Delete, Append and Increment all extend Mutation; RowMutations groups mutations.
    if (action instanceof Mutation || action instanceof RowMutations) {
      return MUTATE;
    }
    return null;
  }

  public abstract String getOperation();

  @Nullable
  public abstract Long getOperationBatchSize();
}
