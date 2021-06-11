/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JdbcNetAttributesExtractor extends NetAttributesExtractor<DbRequest, Void> {

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
