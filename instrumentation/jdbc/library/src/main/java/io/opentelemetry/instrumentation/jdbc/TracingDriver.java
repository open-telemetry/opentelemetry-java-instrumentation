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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.jdbc.parser.URLParser;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TracingDriver implements Driver {

  private static final Driver INSTANCE = new TracingDriver();

  protected static final String TRACE_WITH_ACTIVE_SPAN_ONLY = "traceWithActiveSpanOnly";

  protected static final String WITH_ACTIVE_SPAN_ONLY = TRACE_WITH_ACTIVE_SPAN_ONLY + "=true";

  public static final String IGNORE_FOR_TRACING_REGEX = "ignoreForTracing=\"((?:\\\\\"|[^\"])*)\"[;]*";

  protected static final Pattern PATTERN_FOR_IGNORING = Pattern.compile(IGNORE_FOR_TRACING_REGEX);

  static {
    try {
      DriverManager.registerDriver(INSTANCE);
    } catch (SQLException e) {
      throw new IllegalStateException("Could not register TracingDriver with DriverManager", e);
    }
  }

  /**
   * @return The singleton instance of the {@code TracingDriver}.
   */
  public static Driver load() {
    return INSTANCE;
  }

  /**
   * Ensure {@code TracingDriver} be the first driver of {@link DriverManager} to make sure
   * "interceptor mode" works. WARNING: Driver like Oracle JDBC may fail since it's destroyed
   * forever after deregistration.
   */
  public synchronized static void ensureRegisteredAsTheFirstDriver() {
    try {
      Enumeration<Driver> enumeration = DriverManager.getDrivers();
      List<Driver> drivers = null;
      for (int i = 0; enumeration.hasMoreElements(); ++i) {
        Driver driver = enumeration.nextElement();
        if (i == 0) {
          if (driver == INSTANCE) {
            return;
          }
          drivers = new ArrayList<>();
        }
        if (driver != INSTANCE) {
          drivers.add(driver);
          DriverManager.deregisterDriver(driver);
        }
      }
      for (Driver driver : drivers) {
        DriverManager.registerDriver(driver);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not register TracingDriver with DriverManager", e);
    }
  }

  /**
   * Sets the {@code traceEnabled} property to enable or disable traces.
   *
   * @param traceEnabled The {@code traceEnabled} value.
   */
  public static void setTraceEnabled(boolean traceEnabled) {
    JdbcTracing.setTraceEnabled(traceEnabled);
  }

  public static boolean isTraceEnabled() {
    return JdbcTracing.isTraceEnabled();
  }

  private static boolean interceptorMode = false;

  /**
   * Turns "interceptor mode" on or off.
   *
   * @param interceptorMode The {@code interceptorMode} value.
   */
  public static void setInterceptorMode(final boolean interceptorMode) {
    TracingDriver.interceptorMode = interceptorMode;
  }

  private static boolean withActiveSpanOnly;

  /**
   * Sets the {@code withActiveSpanOnly} property for "interceptor mode".
   *
   * @param withActiveSpanOnly The {@code withActiveSpanOnly} value.
   */
  public static void setInterceptorProperty(final boolean withActiveSpanOnly) {
    TracingDriver.withActiveSpanOnly = withActiveSpanOnly;
  }

  private static Set<String> ignoreStatements;

  /**
   * Sets the {@code ignoreStatements} property for "interceptor mode".
   *
   * @param ignoreStatements The {@code ignoreStatements} value.
   */
  public static void setInterceptorProperty(final Set<String> ignoreStatements) {
    TracingDriver.ignoreStatements = ignoreStatements;
  }

  protected Tracer tracer;

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    // if there is no url, we have problems
    if (url == null) {
      throw new SQLException("url is required");
    }

    final Set<String> ignoreStatements;
    final boolean withActiveSpanOnly;
    if (interceptorMode) {
      withActiveSpanOnly = TracingDriver.withActiveSpanOnly;
      ignoreStatements = TracingDriver.ignoreStatements;
    } else if (acceptsURL(url)) {
      withActiveSpanOnly = url.contains(WITH_ACTIVE_SPAN_ONLY);
      ignoreStatements = extractIgnoredStatements(url);
    } else {
      return null;
    }

    url = extractRealUrl(url);

    // find the real driver for the URL
    final Driver wrappedDriver = findDriver(url);

    final Tracer currentTracer = getTracer();
    final ConnectionInfo connectionInfo = URLParser.parse(url);
    final String realUrl = url;
    final Connection connection = JdbcTracingUtils.call("AcquireConnection", () ->
            wrappedDriver.connect(realUrl, info), null, connectionInfo, withActiveSpanOnly,
        null, currentTracer);

    return WrapperProxy
        .wrap(connection, new TracingConnection(connection, connectionInfo, withActiveSpanOnly,
            ignoreStatements, currentTracer));
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return url != null && (
        url.startsWith(getUrlPrefix()) ||
            (interceptorMode && url.startsWith("jdbc:"))
    );
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return findDriver(url).getPropertyInfo(url, info);
  }

  @Override
  public int getMajorVersion() {
    // There is no way to get it from wrapped driver
    return 1;
  }

  @Override
  public int getMinorVersion() {
    // There is no way to get it from wrapped driver
    return 0;
  }

  @Override
  public boolean jdbcCompliant() {
    return true;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    // There is no way to get it from wrapped driver
    return null;
  }

  public void setTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  protected String getUrlPrefix() {
    return "jdbc:tracing:";
  }

  protected Driver findDriver(String realUrl) throws SQLException {
    if (realUrl == null || realUrl.trim().length() == 0) {
      throw new IllegalArgumentException("url is required");
    }

    for (Driver candidate : Collections.list(DriverManager.getDrivers())) {
      try {
        if (!(candidate instanceof TracingDriver) && candidate.acceptsURL(realUrl)) {
          return candidate;
        }
      } catch (SQLException ignored) {
        // intentionally ignore exception
      }
    }

    throw new SQLException("Unable to find a driver that accepts url: " + realUrl);
  }

  protected String extractRealUrl(String url) {
    String extracted = url.startsWith(getUrlPrefix()) ? url.replace(getUrlPrefix(), "jdbc:") : url;
    return extracted.replaceAll(TRACE_WITH_ACTIVE_SPAN_ONLY + "=(true|false)[;]*", "")
        .replaceAll(IGNORE_FOR_TRACING_REGEX, "")
        .replaceAll("\\?$", "");
  }

  protected Set<String> extractIgnoredStatements(String url) {

    final Matcher matcher = PATTERN_FOR_IGNORING.matcher(url);

    Set<String> results = new HashSet<>(8);

    while (matcher.find()) {
      String rawValue = matcher.group(1);
      String finalValue = rawValue.replace("\\\"", "\"");
      results.add(finalValue);
    }

    return results;
  }

  Tracer getTracer() {
    if (tracer == null) {
      return GlobalOpenTelemetry.get().getTracer("opentelemetry-jdbc", "0.1.0");
    }
    return tracer;
  }

}
