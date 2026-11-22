/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import javax.annotation.Nullable;

@AutoValue
public abstract class ClickHouseDbRequest {
  // The selected peer can change as an operation retries. It is intentionally excluded from
  // AutoValue's equality, hash code, and string representations.
  @Nullable private volatile ServerTarget peer;

  public static ClickHouseDbRequest create(
      @Nullable ServerTarget peer,
      @Nullable ServerTarget serverTarget,
      @Nullable String namespace,
      String sql) {
    ClickHouseDbRequest request = new AutoValue_ClickHouseDbRequest(serverTarget, namespace, sql);
    request.peer = peer;
    return request;
  }

  @Nullable
  public final String getPeerAddress() {
    ServerTarget peer = this.peer;
    return peer == null ? null : peer.getAddress();
  }

  @Nullable
  public final Integer getPeerPort() {
    ServerTarget peer = this.peer;
    return peer == null ? null : peer.getPort();
  }

  public final void setPeer(@Nullable ServerTarget peer) {
    this.peer = peer;
  }

  @Nullable
  public abstract ServerTarget getServerTarget();

  @Nullable
  public abstract String getNamespace();

  public abstract String getSql();
}
