/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class DataSourcePostProcessorTest {

  private static final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

  static {
    beanFactory.registerSingleton("openTelemetry", OpenTelemetry.noop());
    beanFactory.registerSingleton(
        "configProperties", DefaultConfigProperties.createFromMap(Collections.emptyMap()));
  }

  @Test
  @DisplayName("when processed bean is NOT of type DataSource should return Object unchanged")
  void returnsObject() {
    BeanPostProcessor underTest =
        new DataSourcePostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    Object nonDataSource = new Object();
    assertThat(underTest.postProcessAfterInitialization(nonDataSource, "testObject"))
        .isSameAs(nonDataSource);
  }

  @Test
  @DisplayName("when processed bean is of type DataSource should return DataSource proxy")
  void returnsDataSourceProxy() {
    BeanPostProcessor underTest =
        new DataSourcePostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    DataSource originalDataSource = new TestDataSource();

    Object result = underTest.postProcessAfterInitialization(originalDataSource, "testDataSource");

    assertThat(result).isInstanceOf(DataSource.class);
    assertThat(result).isNotSameAs(originalDataSource);

    Object target = AopProxyUtils.getSingletonTarget(result);
    assertThat(target).isSameAs(originalDataSource);
  }

  @Test
  @DisplayName("when processed bean is scoped proxy target should return unchanged")
  void returnsScopedProxyTargetUnchanged() {
    BeanPostProcessor underTest =
        new DataSourcePostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    DataSource dataSource = new TestDataSource();
    String scopedTargetBeanName = "scopedTarget.testDataSource";

    Object result = underTest.postProcessAfterInitialization(dataSource, scopedTargetBeanName);

    assertThat(result).isSameAs(dataSource);
  }

  @Test
  @DisplayName("proxy should delegate method calls to wrapped telemetry DataSource")
  void proxyDelegatesMethodCalls() throws SQLException {
    BeanPostProcessor underTest =
        new DataSourcePostProcessor(
            beanFactory.getBeanProvider(OpenTelemetry.class),
            beanFactory.getBeanProvider(ConfigProperties.class));

    DataSource originalDataSource = new TestDataSource();

    Object result = underTest.postProcessAfterInitialization(originalDataSource, "testDataSource");

    DataSource proxiedDataSource = (DataSource) result;
    Connection connection = proxiedDataSource.getConnection();

    assertThat(connection).isNotNull();
  }

  private static class TestDataSource implements DataSource {
    @Override
    public Connection getConnection() throws SQLException {
      Connection mockConnection = mock(Connection.class);
      when(mockConnection.getMetaData()).thenReturn(mock(java.sql.DatabaseMetaData.class));
      return mockConnection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {}

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {}

    @Override
    public int getLoginTimeout() throws SQLException {
      return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      throw new SQLException("Not supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return false;
    }
  }
}
