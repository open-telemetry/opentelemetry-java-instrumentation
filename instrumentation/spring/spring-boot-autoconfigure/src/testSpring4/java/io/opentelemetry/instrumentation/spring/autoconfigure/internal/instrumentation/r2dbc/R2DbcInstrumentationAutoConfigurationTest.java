/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.r2dbc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractR2DbcInstrumentationAutoConfigurationTest;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class R2DbcInstrumentationAutoConfigurationTest
    extends AbstractR2DbcInstrumentationAutoConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              InstrumentationConfig.class,
              () ->
                  new ConfigPropertiesBridge(
                      DefaultConfigProperties.createFromMap(Collections.emptyMap())))
          .withConfiguration(
              AutoConfigurations.of(
                  R2dbcInstrumentationAutoConfiguration.class, R2dbcAutoConfiguration.class))
          .withBean("openTelemetry", OpenTelemetry.class, testing::getOpenTelemetry);

  @Override
  protected LibraryInstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected ApplicationContextRunner contextRunner() {
    return contextRunner;
  }
}
