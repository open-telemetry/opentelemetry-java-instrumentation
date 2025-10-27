/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Collections.singleton;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(CassandraRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.CASSANDRA;
  }

  @Override
  @Nullable
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Override
  public Collection<String> getRawQueryTexts(CassandraRequest request) {
    return singleton(request.getQueryText());
  }

  @Nullable
  @Override
  public String getServerAddress(CassandraRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(CassandraRequest request) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      CassandraRequest request, @Nullable ExecutionInfo response) {
    if (response == null) {
      return null;
    }
    Host queriedHost = response.getQueriedHost();
    if (queriedHost == null) {
      return null;
    }
    InetSocketAddress address = queriedHost.getSocketAddress();
    if (address != null && address.getAddress() == null) {
      // Address is unresolved, need to resolve it explicitly
      try {
        InetAddress resolved = InetAddress.getByName(address.getHostString());
        return new InetSocketAddress(resolved, address.getPort());
      } catch (UnknownHostException e) {
        // If resolution fails, return the unresolved address anyway
        return address;
      }
    }
    return address;
  }
}
