/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

final class DataSourcePostProcessor implements BeanPostProcessor, Ordered {

  private static final Class<?> ROUTING_DATA_SOURCE_CLASS = getRoutingDataSourceClass();

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  DataSourcePostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  private static Class<?> getRoutingDataSourceClass() {
    try {
      return Class.forName("org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private static boolean isRoutingDatasource(Object bean) {
    return ROUTING_DATA_SOURCE_CLASS != null && ROUTING_DATA_SOURCE_CLASS.isInstance(bean);
  }

  @CanIgnoreReturnValue
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    // Exclude scoped proxy beans to avoid double wrapping
    if (bean instanceof DataSource
        && !isRoutingDatasource(bean)
        && !ScopedProxyUtils.isScopedTarget(beanName)) {
      DataSource dataSource = (DataSource) bean;
      DataSource otelDataSource =
          JdbcTelemetry.builder(openTelemetryProvider.getObject())
              .setStatementSanitizationEnabled(
                  InstrumentationConfigUtil.isStatementSanitizationEnabled(
                      configPropertiesProvider.getObject(),
                      "otel.instrumentation.jdbc.statement-sanitizer.enabled"))
              .setCaptureQueryParameters(
                  configPropertiesProvider
                      .getObject()
                      .getBoolean(
                          "otel.instrumentation.jdbc.experimental.capture-query-parameters", false))
              .setTransactionInstrumenterEnabled(
                  configPropertiesProvider
                      .getObject()
                      .getBoolean(
                          "otel.instrumentation.jdbc.experimental.transaction.enabled", false))
              .build()
              .wrap(dataSource);

      // wrap instrumented data source into a proxy that unwraps to the original data source
      // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13512
      return new DataSource$$Wrapper(otelDataSource, dataSource);
    }
    return bean;
  }

  // To be one of the first bean post-processors to be executed
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 20;
  }

  // Wrapper for DataSource that pretends to be a spring aop proxy. $$ in class name is commonly
  // used by bytecode proxies and is tested by
  // org.springframework.aop.support.AopUtils.isAopProxy(). This proxy can be unwrapped with
  // ((Advised) dataSource).getTargetSource().getTarget() and it unwraps to the original data
  // source.
  @SuppressWarnings("checkstyle:TypeName")
  private static class DataSource$$Wrapper extends AdvisedSupport
      implements SpringProxy, DataSource {
    private final DataSource delegate;

    DataSource$$Wrapper(DataSource delegate, DataSource original) {
      this.delegate = delegate;
      setTarget(original);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return delegate.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
    }
  }
}
