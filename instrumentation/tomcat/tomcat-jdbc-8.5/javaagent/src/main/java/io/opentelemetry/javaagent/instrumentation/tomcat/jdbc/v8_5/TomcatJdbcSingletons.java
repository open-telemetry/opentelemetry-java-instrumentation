/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc.v8_5;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

public class TomcatJdbcSingletons {
  private static final VirtualField<PoolProperties, Boolean> POOL_NAME_CONFIGURED =
      VirtualField.find(PoolProperties.class, Boolean.class);

  public static void setPoolNameConfigured(PoolProperties poolProperties, boolean configured) {
    POOL_NAME_CONFIGURED.set(poolProperties, configured);
  }

  public static boolean isPoolNameConfigured(PoolConfiguration poolProperties) {
    if (!(poolProperties instanceof PoolProperties)) {
      return true;
    }
    return Boolean.TRUE.equals(POOL_NAME_CONFIGURED.get((PoolProperties) poolProperties));
  }

  private TomcatJdbcSingletons() {}
}
