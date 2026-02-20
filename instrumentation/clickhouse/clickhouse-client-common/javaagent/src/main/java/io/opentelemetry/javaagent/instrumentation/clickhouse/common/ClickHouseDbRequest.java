/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {

  public static ClickHouseDbRequest create(
      @Nullable String host, @Nullable Integer port, @Nullable String namespace, String sql) {
    return new AutoValue_ClickHouseDbRequest(host, port, namespace, sql);
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public abstract String getNamespace();

  public abstract String getSql();
}
