/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DistributionSummaryTest extends AbstractDistributionSummaryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing);

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  // the decaying max is not exported under the v3 preview, but it must still be readable through
  // the
  // micrometer api for consumers such as spring boot actuator
  @Test
  void testMaxIsStillReadable() {
    DistributionSummary summary =
        DistributionSummary.builder("testMaxSummary").register(Metrics.globalRegistry);

    summary.record(1);
    summary.record(2);
    summary.record(4);

    assertThat(summary.max()).isEqualTo(4);
  }
}
