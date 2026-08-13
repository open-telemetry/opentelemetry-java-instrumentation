/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.tomcat.dbcp.dbcp2;

import java.util.Properties;
import javax.annotation.Nullable;
import javax.management.ObjectName;

// Helper for accessing non-public BasicDataSource methods from the same package.
public class OpenTelemetryBasicDataSourceUtil {

  @Nullable
  public static ObjectName getRegisteredJmxName(BasicDataSource dataSource) {
    return dataSource.getRegisteredJmxName();
  }

  public static Properties getConnectionProperties(BasicDataSource dataSource) {
    return dataSource.getConnectionProperties();
  }

  private OpenTelemetryBasicDataSourceUtil() {}
}
