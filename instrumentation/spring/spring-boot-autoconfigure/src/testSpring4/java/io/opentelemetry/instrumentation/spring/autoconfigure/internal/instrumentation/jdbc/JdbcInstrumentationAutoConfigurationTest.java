/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.jdbc;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractJdbcInstrumentationAutoConfigurationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

class JdbcInstrumentationAutoConfigurationTest
    extends AbstractJdbcInstrumentationAutoConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(
        JdbcInstrumentationSpringBoot4AutoConfiguration.class, DataSourceAutoConfiguration.class);
  }
}
