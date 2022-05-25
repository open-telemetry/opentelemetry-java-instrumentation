/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.viburdbcp.AbstractViburInstrumentationTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.vibur.dbcp.ViburDBCPDataSource;

class ViburInstrumentationTest extends AbstractViburInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(ViburDBCPDataSource viburDataSource) {}
}
