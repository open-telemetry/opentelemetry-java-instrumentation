/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.instrumentation.hikaricp.AbstractHikariInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.RegisterExtension;

class HikariInstrumentationTest extends AbstractHikariInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(HikariConfig poolConfig, @Nullable MetricsTrackerFactory userTracker) {
    if (userTracker != null) {
      poolConfig.setMetricsTrackerFactory(userTracker);
    }
  }
}
