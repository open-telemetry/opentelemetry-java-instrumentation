/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing.instrumentation.log4j.contextdata.v2_17;

import io.opentelemetry.instrumentation.log4j.contextdata.Log4j2Test;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class AutoLog4j2Test extends Log4j2Test {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }
}
