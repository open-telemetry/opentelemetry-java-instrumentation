/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcNetAttributesGetter implements NetClientAttributesGetter<DbRequest, Void> {

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
