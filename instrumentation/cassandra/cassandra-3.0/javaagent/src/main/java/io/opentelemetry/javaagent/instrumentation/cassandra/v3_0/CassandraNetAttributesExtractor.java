/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ResultSet;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CassandraNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<CassandraRequest, ResultSet> {

  @Override
  @Nullable
  public String transport(CassandraRequest request) {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(
      CassandraRequest request, @Nullable ResultSet response) {
    return response == null
        ? null
        : response.getExecutionInfo().getQueriedHost().getSocketAddress();
  }
}
