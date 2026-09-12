/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import static java.util.logging.Level.FINE;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.management.ObjectName;
import org.apache.commons.dbcp2.BasicDataSource;

// Best-effort reflective accessors for package-private BasicDataSource methods.
public final class BasicDataSourceAccess {

  private static final Logger logger = Logger.getLogger(BasicDataSourceAccess.class.getName());

  @Nullable private static final Method getRegisteredJmxNameMethod;
  @Nullable private static final Method getConnectionPropertiesMethod;

  static {
    getRegisteredJmxNameMethod = findMethod("getRegisteredJmxName");
    getConnectionPropertiesMethod = findMethod("getConnectionProperties");
  }

  @Nullable
  private static Method findMethod(String name) {
    try {
      Method method = BasicDataSource.class.getDeclaredMethod(name);
      method.setAccessible(true);
      return method;
    } catch (Throwable t) {
      logger.log(FINE, "Failed to resolve BasicDataSource." + name + "()", t);
      return null;
    }
  }

  @Nullable
  public static ObjectName getRegisteredJmxName(BasicDataSource dataSource) {
    if (getRegisteredJmxNameMethod == null) {
      return null;
    }
    try {
      return (ObjectName) getRegisteredJmxNameMethod.invoke(dataSource);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to invoke BasicDataSource.getRegisteredJmxName()", t);
      return null;
    }
  }

  public static Properties getConnectionProperties(BasicDataSource dataSource) {
    if (getConnectionPropertiesMethod == null) {
      return new Properties();
    }
    try {
      Properties properties = (Properties) getConnectionPropertiesMethod.invoke(dataSource);
      return properties != null ? properties : new Properties();
    } catch (Throwable t) {
      logger.log(FINE, "Failed to invoke BasicDataSource.getConnectionProperties()", t);
      return new Properties();
    }
  }

  private BasicDataSourceAccess() {}
}
