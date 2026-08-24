/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc.v8_5;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

public class TomcatJdbcSingletons {
  private static final VirtualField<PoolProperties, Boolean> CONFIGURED_POOL_NAME =
      VirtualField.find(PoolProperties.class, Boolean.class);

  public static void setPoolNameConfigured(PoolProperties poolProperties, boolean configured) {
    CONFIGURED_POOL_NAME.set(poolProperties, configured);
  }

  public static boolean isPoolNameConfigured(PoolConfiguration poolProperties) {
    if (!(poolProperties instanceof PoolProperties)) {
      return true;
    }
    return Boolean.TRUE.equals(CONFIGURED_POOL_NAME.get((PoolProperties) poolProperties));
  }

  private TomcatJdbcSingletons() {}
}
