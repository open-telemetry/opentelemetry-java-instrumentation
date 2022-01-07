/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesAdapter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import javax.annotation.Nullable;

public final class JdbcNetAttributesAdapter
    implements NetAttributesAdapter<DbRequest, Void> {

  @Nullable
  @Override
  public String transport(DbRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(DbRequest request, @Nullable Void unused) {
    return request.getDbInfo().getHost();
  }

  @Nullable
  @Override
  public Integer peerPort(DbRequest request, @Nullable Void unused) {
    return request.getDbInfo().getPort();
  }

  @Nullable
  @Override
  public String peerIp(DbRequest request, @Nullable Void unused) {
    return null;
  }
}
