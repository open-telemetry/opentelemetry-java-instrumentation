/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v1_4;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.apache.hadoop.hbase.protobuf.generated.ClientProtos;

@AutoValue
public abstract class HbaseBatchMetadata {

  // Match the operation names reported for the corresponding single operations.
  private static final String GET = "Get";
  private static final String MUTATE = "Mutate";

  // "Multi" is only an internal implementation detail of the native RPC that users are
  // not aware of, so the default semantic-convention "BATCH" prefix is used.
  private static final String BATCH = "BATCH";

  public static HbaseBatchMetadata create(ClientProtos.MultiRequest request) {
    int size = 0;
    String common = null;
    for (ClientProtos.RegionAction regionAction : request.getRegionActionList()) {
      for (ClientProtos.Action action : regionAction.getActionList()) {
        String operation = actionOperation(action);
        if (size == 0) {
          common = operation;
        } else if (common != null && !common.equals(operation)) {
          common = null;
        }
        size++;
      }
    }

    if (size <= 1) {
      // Zero actions have no operation to classify; one action is modeled as non-batch.
      return new AutoValue_HbaseBatchMetadata(common != null ? common : BATCH, null);
    }
    if (common != null) {
      return new AutoValue_HbaseBatchMetadata(BATCH + " " + common, (long) size);
    }
    return new AutoValue_HbaseBatchMetadata(BATCH, (long) size);
  }

  @Nullable
  private static String actionOperation(ClientProtos.Action action) {
    if (action.hasGet()) {
      return GET;
    }
    if (action.hasMutation()) {
      return MUTATE;
    }
    return null;
  }

  public abstract String getOperation();

  @Nullable
  public abstract Long getOperationBatchSize();
}
