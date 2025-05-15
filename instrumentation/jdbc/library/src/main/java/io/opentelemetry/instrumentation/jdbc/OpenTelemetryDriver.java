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

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.INSTRUMENTATION_NAME;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** JDBC driver for OpenTelemetry. */
public final class OpenTelemetryDriver implements Driver {

  // visible for testing
  static final OpenTelemetryDriver INSTANCE = new OpenTelemetryDriver();

  private volatile OpenTelemetry openTelemetry = OpenTelemetry.noop();

  private static final int MAJOR_VERSION;
  private static final int MINOR_VERSION;

  private static final String URL_PREFIX = "jdbc:otel:";
  private static final AtomicBoolean REGISTERED = new AtomicBoolean();
  private static final List<Driver> DRIVER_CANDIDATES = new CopyOnWriteArrayList<>();

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

  /**
   * Add driver candidate that wasn't registered against {@link DriverManager}. This is mainly
   * useful when drivers are initialized at native image build time, and you don't want to postpone
   * class initialization, for runtime initialization could make driver incompatible with other
   * systems. Drivers registered with this method have higher priority over those registered against
   * {@link DriverManager}.
   *
   * @param driver {@link Driver} that should be registered
   */
  public static void addDriverCandidate(Driver driver) {
    if (driver != null) {
      DRIVER_CANDIDATES.add(driver);
    }
  }

  /**
   * Remove driver added via {@link #addDriverCandidate(Driver)}.
   *
   * @param driver {@link Driver} that should be unregistered
   * @return true if the driver was unregistered
   */
  public static boolean removeDriverCandidate(Driver driver) {
    return DRIVER_CANDIDATES.remove(driver);
  }

  /**
   * Find driver that accepts {@code realUrl}. Drivers registered against {@link #DRIVER_CANDIDATES}
   * are preferred over {@link DriverManager} drivers.
   */
  static Driver findDriver(String realUrl) {
    Driver driver = null;
    if (!DRIVER_CANDIDATES.isEmpty()) {
      driver = findDriver(realUrl, DRIVER_CANDIDATES);
    }
    if (driver == null) {
      driver = findDriver(realUrl, Collections.list(DriverManager.getDrivers()));
    }
    if (driver == null) {
      throw new IllegalStateException("Unable to find a driver that accepts url: " + realUrl);
    }

    return driver;
  }

  private static Driver findDriver(String realUrl, Collection<Driver> drivers) {
    for (Driver candidate : drivers) {
      try {
        if (!(candidate instanceof OpenTelemetryDriver) && candidate.acceptsURL(realUrl)) {
          return candidate;
        }
      } catch (SQLException ignored) {
        // intentionally ignore exception
      }
    }
    return null;
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
    String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    if (version == null) {
      // return 0.0 as a fallback
      return new int[] {0, 0};
    }
    String[] parts = version.split("\\.");
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

  /**
   * Installs the {@link OpenTelemetry} instance on the {@code OpenTelemetryDriver}. OpenTelemetry
   * has to be set before the initialization of the database connection pool.
   */
  public static void install(OpenTelemetry openTelemetry) {
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      if (driver instanceof io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver) {
        OpenTelemetryDriver openTelemetryDriver = (OpenTelemetryDriver) driver;
        openTelemetryDriver.setOpenTelemetry(openTelemetry);
      }
    }
  }

  /**
   * Configures the {@link OpenTelemetry}. See {@link #install(OpenTelemetry)} for simple
   * installation option. OpenTelemetry has to be set before the initialization of the database
   * connection pool.
   */
  public void setOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
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

    Instrumenter<DbRequest, Void> statementInstrumenter =
        JdbcInstrumenterFactory.createStatementInstrumenter(openTelemetry);
    boolean captureQueryParameters = JdbcInstrumenterFactory.captureQueryParameters();
    Instrumenter<DbRequest, Void> transactionInstrumenter =
        JdbcInstrumenterFactory.createTransactionInstrumenter(openTelemetry);

    return OpenTelemetryConnection.create(
        connection, dbInfo, statementInstrumenter, transactionInstrumenter, captureQueryParameters);
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
