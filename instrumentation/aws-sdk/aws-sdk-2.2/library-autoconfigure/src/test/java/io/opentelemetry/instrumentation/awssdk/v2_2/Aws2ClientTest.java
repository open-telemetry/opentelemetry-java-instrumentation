/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

class Aws2ClientTest extends AbstractAws2ClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder();
  }
}
