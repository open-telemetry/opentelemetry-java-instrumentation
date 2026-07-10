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

  // "Multi" is only an internal implementation detail of the native RPC (rpc Multi in
  // ClientService) that users are not aware of; the user-facing API is Table.batch(...). The
  // default semantic-convention "BATCH" prefix is used instead.
  private static final String BATCH = "BATCH";

  // The caller only invokes this for a non-empty batch under stable semconv, so the first
  // action is always present.
  public static HbaseBatchMetadata create(List<? extends Row> actions) {
    int size = actions.size();

    // common is the shared operation name, or null if the action types differ or any action is of
    // an unrecognized type; a non-null value after the loop means the batch is homogeneous.
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
