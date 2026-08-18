/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static java.util.logging.Level.FINE;

import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class CassandraEndPoints {

  private static final Logger logger = Logger.getLogger(CassandraEndPoints.class.getName());

  @Nullable private static final Field PROXY_ADDRESS_FIELD = getProxyAddressField();

  @Nullable
  static InetSocketAddress getProxyAddress(SniEndPoint endPoint) {
    if (PROXY_ADDRESS_FIELD == null) {
      return null;
    }
    Object object;
    try {
      object = PROXY_ADDRESS_FIELD.get(endPoint);
    } catch (IllegalAccessException e) {
      logger.log(
          FINE,
          "Error when accessing the private field proxyAddress of SniEndPoint using reflection.",
          e);
      return null;
    }
    return object instanceof InetSocketAddress ? (InetSocketAddress) object : null;
  }

  @Nullable
  private static Field getProxyAddressField() {
    try {
      Field field = SniEndPoint.class.getDeclaredField("proxyAddress");
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      return null;
    }
  }

  private CassandraEndPoints() {}
}
