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
  @Nullable private volatile DbServerTarget peer;

  public static ClickHouseDbRequest create(
      @Nullable String host,
      @Nullable Integer port,
      @Nullable DbServerTarget peer,
      @Nullable DbServerTarget serverTarget,
      @Nullable String namespace,
      String sql) {
    ClickHouseDbRequest request =
        new AutoValue_ClickHouseDbRequest(host, port, serverTarget, namespace, sql);
    request.peer = peer;
    return request;
  }

  @Nullable
  public abstract String getHost();

  @Nullable
  public abstract Integer getPort();

  @Nullable
  public final String getPeerAddress() {
    DbServerTarget peer = this.peer;
    return peer == null ? null : peer.getAddress();
  }

  @Nullable
  public final Integer getPeerPort() {
    DbServerTarget peer = this.peer;
    return peer == null ? null : peer.getPort();
  }

  public final void setPeer(@Nullable DbServerTarget peer) {
    this.peer = peer;
  }

  @Nullable
  public abstract DbServerTarget getServerTarget();

  @Nullable
  public abstract String getNamespace();

  public abstract String getSql();
}
