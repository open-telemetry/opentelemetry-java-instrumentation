/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static java.util.Collections.singletonList;

import io.nats.client.Connection;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class NatsInstrumentationExperimentalTest extends AbstractNatsInstrumentationExperimentalTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final NatsTelemetry telemetry;
  static final Connection natsConnection;

  static {
    telemetry =
        NatsTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveInstrumentationEnabled(true)
            .setCapturedHeaders(singletonList("captured-header"))
            .build();
    natsConnection = telemetry.wrap(new TestConnection());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection connection() {
    return natsConnection;
  }
}
