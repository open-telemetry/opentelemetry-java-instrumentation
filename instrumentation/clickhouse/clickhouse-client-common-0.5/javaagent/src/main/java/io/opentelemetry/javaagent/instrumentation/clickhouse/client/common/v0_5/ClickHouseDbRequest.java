/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {

  public static ClickHouseDbRequest create(
      @Nullable String host,
      @Nullable Integer port,
      @Nullable DbServerTarget serverTarget,
      @Nullable String namespace,
      String sql) {
    return new AutoValue_ClickHouseDbRequest(host, port, serverTarget, namespace, sql);
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public abstract DbServerTarget getServerTarget();

  @Nullable
  public abstract String getNamespace();

  public abstract String getSql();
}
