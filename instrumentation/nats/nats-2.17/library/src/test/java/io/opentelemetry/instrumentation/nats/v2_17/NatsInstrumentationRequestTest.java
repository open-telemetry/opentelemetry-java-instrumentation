/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
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
  static void beforeAll() {
    connection = NatsTelemetry.create(testing.getOpenTelemetry()).wrap(connection);
  }

  @Override
  protected boolean isInboxMonitored() {
    // in the library instrumentation, as we're proxying Connection,
    // we can not properly instrument the Dispatcher and the MessageHandler
    // created for every `request` on the _INBOX.* subjects
    return false;
  }
}
