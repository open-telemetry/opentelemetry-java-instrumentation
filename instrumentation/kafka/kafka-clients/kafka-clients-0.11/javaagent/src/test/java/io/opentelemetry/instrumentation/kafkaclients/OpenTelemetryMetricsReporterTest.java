/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.kafka.internal.AbstractOpenTelemetryMetricsReporterTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Map;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@EnabledIfSystemProperty(
    named = "testLatestDeps",
    matches = "true",
    disabledReason =
        "kafka-clients 0.11 emits a significantly different set of metrics; it's probably fine to just test the latest version")
class OpenTelemetryMetricsReporterTest extends AbstractOpenTelemetryMetricsReporterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Map<String, ?> additionalConfig() {
    return emptyMap();
  }
}
