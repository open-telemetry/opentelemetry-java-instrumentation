/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nifi;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
public abstract class AbstractNifiInstrumentationTest {

  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-nifi-1.24.0";

  protected abstract InstrumentationExtension testing();

  @BeforeAll
  static void setUpMocks() throws SQLException {
    MockDriver.register();
  }
}
