/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.redisson.connection.MasterSlaveConnectionManager;

public final class RedissonDatabaseIndex {
  private static final VirtualField<MasterSlaveConnectionManager, RedissonDatabaseIndex>
      DATABASE_INDEX_FIELD =
          VirtualField.find(MasterSlaveConnectionManager.class, RedissonDatabaseIndex.class);

  private final long value;

  public static void capture(MasterSlaveConnectionManager manager, @Nullable Long databaseIndex) {
    if (databaseIndex == null) {
      return;
    }
    DATABASE_INDEX_FIELD.set(manager, new RedissonDatabaseIndex(databaseIndex));
  }

  @Nullable
  public static Long get(Object manager) {
    if (!(manager instanceof MasterSlaveConnectionManager)) {
      return null;
    }
    RedissonDatabaseIndex databaseIndex =
        DATABASE_INDEX_FIELD.get((MasterSlaveConnectionManager) manager);
    return databaseIndex != null ? databaseIndex.value : null;
  }

  private RedissonDatabaseIndex(long value) {
    this.value = value;
  }
}
