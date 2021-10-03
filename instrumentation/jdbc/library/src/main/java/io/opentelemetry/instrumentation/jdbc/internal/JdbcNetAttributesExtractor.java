/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class JdbcNetAttributesExtractor extends NetAttributesExtractor<DbRequest, Void> {

  @Nullable
  @Override
  public String transport(DbRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(DbRequest request) {
    return request.getDbInfo().getHost();
  }

  @Nullable
  @Override
  public Integer peerPort(DbRequest request) {
    return request.getDbInfo().getPort();
  }

  @Nullable
  @Override
  public String peerIp(DbRequest request) {
    return null;
  }
}
