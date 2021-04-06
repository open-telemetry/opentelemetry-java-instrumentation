/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.instrumenter.InetSocketAddressNetAttributesExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JdbcNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<DbRequest, Void> {
  @Override
  protected @Nullable InetSocketAddress getAddress(DbRequest dbRequest, Void unused) {
    String host = dbRequest.getDbInfo().getHost();
    Integer port = dbRequest.getDbInfo().getPort();
    if (host != null && port != null) {
      return new InetSocketAddress(host, port);
    }
    return null;
  }

  @Override
  protected @Nullable String transport(DbRequest dbRequest) {
    return null;
  }
}
