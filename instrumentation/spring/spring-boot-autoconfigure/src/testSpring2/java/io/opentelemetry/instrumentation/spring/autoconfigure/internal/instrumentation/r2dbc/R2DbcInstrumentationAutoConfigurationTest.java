/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.r2dbc;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractR2DbcInstrumentationAutoConfigurationTest;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;

class R2DbcInstrumentationAutoConfigurationTest
    extends AbstractR2DbcInstrumentationAutoConfigurationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected LibraryInstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(
        R2dbcInstrumentationAutoConfiguration.class, R2dbcAutoConfiguration.class);
  }
}
