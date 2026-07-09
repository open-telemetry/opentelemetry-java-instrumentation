/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.commons.dbcp2;

import javax.annotation.Nullable;
import javax.management.ObjectName;

// Helper for accessing protected BasicDataSource methods from the same package.
public final class OpenTelemetryBasicDataSourceUtil {

  @Nullable
  public static ObjectName getRegisteredJmxName(BasicDataSource dataSource) {
    return dataSource.getRegisteredJmxName();
  }

  private OpenTelemetryBasicDataSourceUtil() {}
}
