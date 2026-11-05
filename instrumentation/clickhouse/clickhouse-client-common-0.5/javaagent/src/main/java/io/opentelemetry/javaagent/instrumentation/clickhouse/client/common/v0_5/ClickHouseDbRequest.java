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
  // The selected peer can change as an operation retries. It is intentionally excluded from
  // AutoValue's equality, hash code, and string representations.
  @Nullable private volatile DbServerTarget peer;

  public static ClickHouseDbRequest create(
      @Nullable DbServerTarget peer,
      @Nullable DbServerTarget serverTarget,
      @Nullable String namespace,
      String sql) {
    ClickHouseDbRequest request = new AutoValue_ClickHouseDbRequest(serverTarget, namespace, sql);
    request.peer = peer;
    return request;
  }

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
