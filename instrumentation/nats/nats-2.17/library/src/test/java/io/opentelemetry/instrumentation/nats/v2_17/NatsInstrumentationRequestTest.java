/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Nats;
import io.nats.client.Options;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class NatsInstrumentationRequestTest extends AbstractNatsInstrumentationRequestTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void beforeAll() throws IOException, InterruptedException {
    NatsTelemetry telemetry = NatsTelemetry.create(testing.getOpenTelemetry());
    connection =
        telemetry.newConnection(
            Options.builder().server(connection.getConnectedUrl()).build(), Nats::connect);
  }
}
