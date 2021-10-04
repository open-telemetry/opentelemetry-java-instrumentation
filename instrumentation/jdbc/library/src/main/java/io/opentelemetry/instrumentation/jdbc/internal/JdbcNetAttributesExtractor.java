/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class JdbcNetAttributesExtractor extends NetAttributesExtractor<DbRequest, Void> {

  public JdbcNetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_START);
  }

  @Nullable
  @Override
  public String transport(DbRequest request) {
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
