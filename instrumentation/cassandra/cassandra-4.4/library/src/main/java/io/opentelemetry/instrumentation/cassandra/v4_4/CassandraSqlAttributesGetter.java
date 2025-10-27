/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static java.util.Collections.singleton;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter
    implements SqlClientAttributesGetter<CassandraRequest, ExecutionInfo> {
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String CASSANDRA = "cassandra";

  private static final Logger logger =
      Logger.getLogger(CassandraSqlAttributesGetter.class.getName());

  private static final Field proxyAddressField = getProxyAddressField();

  @Override
  public String getDbSystem(CassandraRequest request) {
    return CASSANDRA;
  }

  @Override
  @Nullable
  public String getDbNamespace(CassandraRequest request) {
    return request.getSession().getKeyspace().map(CqlIdentifier::toString).orElse(null);
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
    Node coordinator = response.getCoordinator();
    if (coordinator == null) {
      return null;
    }
    EndPoint endPoint = coordinator.getEndPoint();
    InetSocketAddress address = null;
    if (endPoint instanceof DefaultEndPoint) {
      address = ((DefaultEndPoint) endPoint).resolve();
    } else if (endPoint instanceof SniEndPoint && proxyAddressField != null) {
      SniEndPoint sniEndPoint = (SniEndPoint) endPoint;
      Object object = null;
      try {
        object = proxyAddressField.get(sniEndPoint);
      } catch (Exception e) {
        logger.log(
            Level.FINE,
            "Error when accessing the private field proxyAddress of SniEndPoint using reflection.",
            e);
      }
      if (object instanceof InetSocketAddress) {
        address = (InetSocketAddress) object;
      }
    }

    // Ensure address is resolved
    if (address != null && address.getAddress() == null) {
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

  @Nullable
  private static Field getProxyAddressField() {
    try {
      Field field = SniEndPoint.class.getDeclaredField("proxyAddress");
      field.setAccessible(true);
      return field;
    } catch (Exception e) {
      return null;
    }
  }
}
