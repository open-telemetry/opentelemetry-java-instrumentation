/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.common;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class HbaseRequest {

  public static HbaseRequest create(
      @Nullable String operation,
      @Nullable String table,
      @Nullable String user,
      @Nullable String host,
      @Nullable Integer port) {
    return new AutoValue_HbaseRequest(operation, table, user, host, port);
  }

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract String getTable();

  @Nullable
  public abstract String getUser();

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();
}
