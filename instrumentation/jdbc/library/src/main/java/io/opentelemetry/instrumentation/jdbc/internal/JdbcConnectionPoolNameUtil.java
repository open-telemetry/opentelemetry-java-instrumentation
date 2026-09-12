/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.Properties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 *
 * @deprecated Use {@link JdbcConnectionPoolMetricsUtil} instead.
 */
@Deprecated
public final class JdbcConnectionPoolNameUtil {

  public static String poolName(Properties properties, String fallbackName) {
    return JdbcConnectionPoolMetricsUtil.poolName(
        JdbcConnectionPoolMetricsUtil.dbInfo(properties), null, fallbackName);
  }

  public static String poolName(DbInfo dbInfo, String fallbackName) {
    return JdbcConnectionPoolMetricsUtil.poolName(dbInfo, null, fallbackName);
  }

  private JdbcConnectionPoolNameUtil() {}
}
