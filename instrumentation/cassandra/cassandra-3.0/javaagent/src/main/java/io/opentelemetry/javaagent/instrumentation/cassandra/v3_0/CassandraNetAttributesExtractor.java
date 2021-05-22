/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CassandraNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<CassandraRequest, Void> {

  @Override
  @Nullable
  protected String transport(CassandraRequest request) {
    return null;
  }

  @Override
  protected @Nullable InetSocketAddress getAddress(
      CassandraRequest request, @Nullable Void response) {
    ExecutionInfo executionInfo = request.getExecutionInfo();
    return executionInfo == null ? null : executionInfo.getQueriedHost().getSocketAddress();
  }
}
