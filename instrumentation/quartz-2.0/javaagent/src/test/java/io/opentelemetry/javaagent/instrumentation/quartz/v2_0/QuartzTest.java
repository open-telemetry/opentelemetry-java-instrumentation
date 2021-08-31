/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.quartz.v2_0.AbstractQuartzTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.quartz.Scheduler;

class QuartzTest extends AbstractQuartzTest {

  @RegisterExtension InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected void configureScheduler(Scheduler scheduler) {}

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }
}
