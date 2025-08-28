/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class NatsInstrumentationDispatcherTest extends AbstractNatsInstrumentationDispatcherTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static NatsTelemetry telemetry;
  static Connection natsConnection;

  @BeforeAll
  static void beforeAll() {
    telemetry = NatsTelemetry.create(testing.getOpenTelemetry());
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
