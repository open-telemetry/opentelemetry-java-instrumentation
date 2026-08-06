/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TimerTest extends AbstractTimerTest {

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
    Timer timer = Timer.builder("testMaxTimer").register(Metrics.globalRegistry);

    timer.record(Duration.ofSeconds(42));

    assertThat(timer.max(SECONDS)).isEqualTo(42);
  }
}
