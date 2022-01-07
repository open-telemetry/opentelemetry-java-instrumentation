/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** JDBC driver for OpenTelemetry. */
public final class OpenTelemetryDriver implements Driver {

  // visible for testing
  static final OpenTelemetryDriver INSTANCE = new OpenTelemetryDriver();

  private static final int MAJOR_VERSION;
  private static final int MINOR_VERSION;

  private static final String URL_PREFIX = "jdbc:otel:";
  private static final AtomicBoolean REGISTERED = new AtomicBoolean();

  static {
    try {
      int[] version = parseInstrumentationVersion();
      MAJOR_VERSION = version[0];
      MINOR_VERSION = version[1];

      register();
    } catch (SQLException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Register the driver against {@link DriverManager}. This is done automatically when the class is
   * loaded. Dropping the driver from DriverManager's list is possible using {@link #deregister()}
   * method.
   *
   * @throws IllegalStateException if the driver is already registered
   * @throws SQLException if registering the driver fails
   */
  public static void register() throws SQLException {
    if (!REGISTERED.compareAndSet(false, true)) {
      throw new IllegalStateException(
          "Driver is already registered. It can only be registered once.");
    }
    DriverManager.registerDriver(INSTANCE);
  }

  /**
   * According to JDBC specification, this driver is registered against {@link DriverManager} when
   * the class is loaded. To avoid leaks, this method allow unregistering the driver so that the
   * class can be gc'ed if necessary.
   *
   * @throws IllegalStateException if the driver is not registered
   * @throws SQLException if deregistering the driver fails
   */
  public static void deregister() throws SQLException {
    if (!REGISTERED.compareAndSet(true, false)) {
      throw new IllegalStateException(
          "Driver is not registered (or it has not been registered using Driver.register() method)");
    }
    DriverManager.deregisterDriver(INSTANCE);
  }

  /** Returns {@code true} if the driver is registered against {@link DriverManager}. */
  public static boolean isRegistered() {
    return REGISTERED.get();
  }

  private static Driver findDriver(String realUrl) {
    for (Driver candidate : Collections.list(DriverManager.getDrivers())) {
      try {
        if (!(candidate instanceof OpenTelemetryDriver) && candidate.acceptsURL(realUrl)) {
          return candidate;
        }
      } catch (SQLException ignored) {
        // intentionally ignore exception
      }
    }

    throw new IllegalStateException("Unable to find a driver that accepts url: " + realUrl);
  }

  /**
   * Parses out the real JDBC connection URL by removing "otel:" prefix.
   *
   * @param url the connection URL
   * @return the parsed URL
   */
  private static String extractRealUrl(String url) {
    return url.startsWith(URL_PREFIX) ? url.replace(URL_PREFIX, "jdbc:") : url;
  }

  private static int[] parseInstrumentationVersion() {
    String[] parts = InstrumentationVersion.VERSION.split("\\.");
    if (parts.length >= 2) {
      try {
        int majorVersion = Integer.parseInt(parts[0]);
        int minorVersion = Integer.parseInt(parts[1]);

        return new int[] {majorVersion, minorVersion};
      } catch (NumberFormatException ignored) {
        // ignore incorrect version
      }
    }
    // return 0.0 as a fallback
    return new int[] {0, 0};
  }

  @Nullable
  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("url is required");
    }

    if (!acceptsURL(url)) {
      return null;
    }

    String realUrl = extractRealUrl(url);

    // find the real driver for the URL
    Driver wrappedDriver = findDriver(realUrl);

    Connection connection = wrappedDriver.connect(realUrl, info);

    DbInfo dbInfo = JdbcConnectionUrlParser.parse(realUrl, info);

    return new OpenTelemetryConnection(connection, dbInfo);
  }

  @Override
  public boolean acceptsURL(String url) {
    if (url == null) {
      return false;
    }
    return url.startsWith(URL_PREFIX) && url.length() > URL_PREFIX.length();
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("url is required");
    }

    String realUrl = extractRealUrl(url);
    Driver wrappedDriver = findDriver(realUrl);
    return wrappedDriver.getPropertyInfo(realUrl, info);
  }

  @Override
  public int getMajorVersion() {
    return MAJOR_VERSION;
  }

  @Override
  public int getMinorVersion() {
    return MINOR_VERSION;
  }

  /** Returns {@literal false} because not all delegated drivers are JDBC compliant. */
  @Override
  public boolean jdbcCompliant() {
    return false;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException("Feature not supported");
  }
}
