/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Enumeration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OpenTelemetryDriverTest {

  @DisplayName("verify driver auto registered")
  @Order(1) // verifyRemoveDriverCandidate test method removes the drivers
  @Test
  void verifyOpenTelemetryDriverAutoRegistration() {

    Enumeration<Driver> drivers = DriverManager.getDrivers();

    // From JDBC 4.0 (Java SE 6), the driver can be auto-registered from the java.sql.Driver file
    // contained in the META-INF.services folder
    assertTrue(OpenTelemetryDriver.isRegistered());

    assertTrue(hasOpenTelemetryDriver(drivers));
  }

  private static boolean hasOpenTelemetryDriver(Enumeration<Driver> drivers) {
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      if (driver.equals(OpenTelemetryDriver.INSTANCE)) {
        return true;
      }
    }
    return false;
  }

  @DisplayName("verify standard properties")
  @Test
  void verifyStandardProperties() {

    assertThat(OpenTelemetryDriver.INSTANCE.jdbcCompliant()).isFalse();

    String[] parts = Instrumenter.class.getPackage().getImplementationVersion().split("\\.");

    assertThat(OpenTelemetryDriver.INSTANCE.getMajorVersion())
        .isEqualTo(Integer.parseInt(parts[0]));
    assertThat(OpenTelemetryDriver.INSTANCE.getMinorVersion())
        .isEqualTo(Integer.parseInt(parts[1]));
  }

  @DisplayName("verify parent logger thrown an exception")
  @Test
  void verifyParentLoggerThrownAnException() {
    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.getParentLogger())
        .isInstanceOf(SQLFeatureNotSupportedException.class)
        .hasMessage("Feature not supported");
  }

  @DisplayName("verify accepted urls")
  @ParameterizedTest
  @MethodSource("provideInputOutputForAcceptUrl")
  void verifyAcceptedUrls(String input, boolean expected) {
    OpenTelemetryDriver driver = OpenTelemetryDriver.INSTANCE;
    assertThat(driver.acceptsURL(input)).isEqualTo(expected);
  }

  private static Stream<Arguments> provideInputOutputForAcceptUrl() {
    return Stream.of(
        Arguments.of(null, false),
        Arguments.of("", false),
        Arguments.of("jdbc:", false),
        Arguments.of("bogus:string", false),
        Arguments.of("jdbc:postgresql://127.0.0.1:5432/dbname", false),
        Arguments.of("jdbc:otel:postgresql://127.0.0.1:5432/dbname", true));
  }

  @DisplayName("verify connection with null url")
  @Test
  void verifyConnectionWithNullUrl() {
    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.connect(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("url is required");
  }

  @DisplayName("verify connection with empty url")
  @Test
  void verifyConnectionWithEmptyUrl() {
    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.connect(" ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("url is required");
  }

  @DisplayName("verify connection with not accepted url")
  @Test
  void verifyConnectionWithNotAcceptedUrl() throws SQLException {
    Connection connection = OpenTelemetryDriver.INSTANCE.connect("abc:xyz", null);
    assertNull(connection);
  }

  @DisplayName("verify add driver candidate")
  @Test
  void verifyAddDriverCandidate() throws SQLException {
    TestDriver driver = new TestDriver();
    OpenTelemetryDriver.addDriverCandidate(driver);
    Connection connection = OpenTelemetryDriver.INSTANCE.connect("jdbc:otel:test:", null);
    OpenTelemetryDriver.removeDriverCandidate(driver);

    assertThat(connection).isInstanceOf(OpenTelemetryConnection.class);
  }

  @DisplayName("verify remove driver candidate")
  @Test
  void verifyRemoveDriverCandidate() throws SQLException {

    unregisterDrivers();

    TestDriver newDriver = new TestDriver();
    OpenTelemetryDriver.addDriverCandidate(newDriver);
    OpenTelemetryDriver.removeDriverCandidate(newDriver);

    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.connect("jdbc:otel:test:", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to find a driver that accepts url: jdbc:test:");
  }

  private static void unregisterDrivers() throws SQLException {
    Enumeration<Driver> drivers = DriverManager.getDrivers();

    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      DriverManager.deregisterDriver(driver);
    }
  }

  @DisplayName("Driver candidate has higher priority")
  @Test
  void verifyDriverCandidateHasHigherPriority() throws SQLException {

    deregisterTestDriver();
    TestDriver localDriver = new TestDriver();
    TestDriver globalDriver = new TestDriver();

    OpenTelemetryDriver.addDriverCandidate(localDriver);
    DriverManager.registerDriver(globalDriver);
    Driver winner = OpenTelemetryDriver.findDriver("jdbc:test:");
    OpenTelemetryDriver.removeDriverCandidate(localDriver);

    assertThat(winner).isEqualTo(localDriver);
    assertThat(winner).isNotEqualTo(globalDriver);
  }

  @DisplayName("Two clashing driver candidates")
  @Test
  void verifyTwoClashingDriverCandidates() throws SQLException {

    TestDriver localDriver1 = new TestDriver();
    TestDriver localDriver2 = new TestDriver();

    OpenTelemetryDriver.addDriverCandidate(localDriver1);
    OpenTelemetryDriver.addDriverCandidate(localDriver2);

    Driver winner2 = OpenTelemetryDriver.findDriver("jdbc:test:");
    OpenTelemetryDriver.removeDriverCandidate(localDriver1);
    OpenTelemetryDriver.removeDriverCandidate(localDriver2);

    assertThat(winner2).isEqualTo(localDriver1);
    assertThat(winner2).isNotEqualTo(localDriver2);
  }

  @DisplayName("Verify drivers in DriverManager are used as fallback")
  @Test
  void verifyDriversInDriverManagerAreUsedAsFallback() {

    registerTestDriver();
    TestDriver localDriver3 =
        new TestDriver() {
          @Override
          public boolean acceptsURL(String url) {
            return false;
          }
        };
    OpenTelemetryDriver.addDriverCandidate(localDriver3);

    Driver winner3 = OpenTelemetryDriver.findDriver("jdbc:test:");
    OpenTelemetryDriver.removeDriverCandidate(localDriver3);

    assertThat(winner3).isNotNull();
    assertThat(winner3).isNotEqualTo(localDriver3);
  }

  @DisplayName("Verify connection with accepted url")
  @Test
  void verifyConnectionWithAcceptedUrl() throws SQLException {

    registerTestDriver();

    Connection connection2 = OpenTelemetryDriver.INSTANCE.connect("jdbc:otel:test:", null);

    assertThat(connection2).isNotNull();
    assertThat(connection2).isInstanceOf(OpenTelemetryConnection.class);
  }

  @DisplayName("Verify get property info with test driver url")
  @Test
  void verifyGetPropertyInfoWithTestDriverUrl() throws SQLException {

    registerTestDriver();
    String testUrl = "jdbc:otel:test:";
    DriverPropertyInfo[] propertyInfos =
        OpenTelemetryDriver.INSTANCE.getPropertyInfo(testUrl, null);
    assertThat(propertyInfos).hasSize(1);
    assertThat(propertyInfos[0].name).isEqualTo("test");
    assertThat(propertyInfos[0].value).isEqualTo("test");
  }

  private static void deregisterTestDriver() {
    if (hasTestDriver()) {
      try {
        DriverManager.deregisterDriver(new TestDriver());
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static void registerTestDriver() {
    if (!hasTestDriver()) {
      try {
        DriverManager.registerDriver(new TestDriver());
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static boolean hasTestDriver() {
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      if (driver instanceof TestDriver) {
        return true;
      }
    }
    return false;
  }

  @DisplayName("verify get property info with null url")
  @Test
  void verifyGetPropertyInfoWithNullUrl() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OpenTelemetryDriver.INSTANCE.getPropertyInfo(null, null),
        "url is required");
  }

  @DisplayName("verify get property info with empty url")
  @Test
  void verifyGetPropertyInfoWithEmptyUrl() {
    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.getPropertyInfo(" ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("url is required");
  }

  @DisplayName("verify get property info with unknown driver url")
  @Test
  void verifyGetPropertyInfoWithUnknownDriverUrl() {
    String unknownUrl = "jdbc:unknown";
    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.getPropertyInfo(unknownUrl, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to find a driver that accepts url: " + unknownUrl);
  }

  @DisplayName("verify get property info with test driver url")
  @Test
  void verifyGetPropertyInfoWithUnknowDriverUrl() {
    String unknownUrl = "jdbc:unknown";
    assertThatThrownBy(() -> OpenTelemetryDriver.INSTANCE.getPropertyInfo(unknownUrl, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to find a driver that accepts url: " + unknownUrl);
  }
}
