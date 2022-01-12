/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class CassandraNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<CassandraRequest, ExecutionInfo> {

  @Override
  @Nullable
  public String transport(CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    return executionInfo == null ? null : executionInfo.getQueriedHost().getSocketAddress();
  }
}
