/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.r2dbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.r2dbc.v1_0.R2dbcTelemetry;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.r2dbc.OptionsCapableConnectionFactory;

class R2dbcInstrumentingPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final boolean statementSanitizationEnabled;

  R2dbcInstrumentingPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider, boolean statementSanitizationEnabled) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.statementSanitizationEnabled = statementSanitizationEnabled;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof ConnectionFactory && !ScopedProxyUtils.isScopedTarget(beanName)) {
      ConnectionFactory connectionFactory = (ConnectionFactory) bean;
      return R2dbcTelemetry.builder(openTelemetryProvider.getObject())
          .setStatementSanitizationEnabled(statementSanitizationEnabled)
          .build()
          .wrapConnectionFactory(connectionFactory, getConnectionFactoryOptions(connectionFactory));
    }
    return bean;
  }

  private static ConnectionFactoryOptions getConnectionFactoryOptions(
      ConnectionFactory connectionFactory) {
    OptionsCapableConnectionFactory optionsCapableConnectionFactory =
        OptionsCapableConnectionFactory.unwrapFrom(connectionFactory);
    if (optionsCapableConnectionFactory != null) {
      return optionsCapableConnectionFactory.getOptions();
    } else {
      // in practice should never happen
      // fall back to empty options; or reconstruct them from the R2dbcProperties
      return ConnectionFactoryOptions.builder().build();
    }
  }
}
