/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class NatsDispatcherTest extends AbstractNatsDispatcherTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  void wrapConnection() {
    connection =
        NatsTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(
                IncludeExclude.builder()
                    .setIncluded("Test-Message-*")
                    .setExcluded("*-Excluded-Header")
                    .build())
            .build()
            .wrap(connection);
  }
}
