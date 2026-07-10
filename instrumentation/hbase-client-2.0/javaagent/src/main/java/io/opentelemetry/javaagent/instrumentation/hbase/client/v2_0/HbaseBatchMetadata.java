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

  // Match the operation names reported for the corresponding single operations.
  private static final String GET = "Get";
  private static final String MUTATE = "Mutate";

  // "Multi" is only an internal implementation detail of the native RPC that users are
  // not aware of, so the default semantic-convention "BATCH" prefix is used.
  private static final String BATCH = "BATCH";

  // The caller only invokes this for a non-empty batch under stable semconv, so the first
  // action is always present.
  public static HbaseBatchMetadata create(List<? extends Row> actions) {
    int size = actions.size();

    String common = actionOperation(actions.get(0));
    for (int i = 1; common != null && i < size; i++) {
      if (!common.equals(actionOperation(actions.get(i)))) {
        common = null;
      }
    }

    if (size == 1) {
      // a single operation is modeled as a non-batch operation (no db.operation.batch.size)
      return new AutoValue_HbaseBatchMetadata(common != null ? common : BATCH, null);
    }
    if (common != null) {
      return new AutoValue_HbaseBatchMetadata(BATCH + " " + common, (long) size);
    }
    return new AutoValue_HbaseBatchMetadata(BATCH, (long) size);
  }

  @Nullable
  private static String actionOperation(Row action) {
    if (action instanceof Get) {
      return GET;
    }
    // Match the "Mutate" reported for single Put/Delete/Append/Increment operations; those all
    // extend Mutation, and RowMutations groups such mutations.
    if (action instanceof Mutation || action instanceof RowMutations) {
      return MUTATE;
    }
    return null;
  }

  public abstract String getOperation();

  @Nullable
  public abstract Long getOperationBatchSize();
}
