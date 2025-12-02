/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractJdbcInstrumentationAutoConfigurationTest;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JdbcInstrumentationAutoConfigurationTest
    extends AbstractJdbcInstrumentationAutoConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  protected InstrumentationExtension testing() {
    return testing;
  }

  protected ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withBean(
            InstrumentationConfig.class,
            () ->
                new ConfigPropertiesBridge(
                    DefaultConfigProperties.createFromMap(Collections.emptyMap())))
        .withConfiguration(
            AutoConfigurations.of(
                JdbcInstrumentationAutoConfiguration.class, DataSourceAutoConfiguration.class))
        .withBean("openTelemetry", OpenTelemetry.class, testing::getOpenTelemetry);
  }
}
