/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public abstract class AbstractAwsSdkTest {
  protected static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes");

  protected abstract InstrumentationExtension getTesting();

  // Plain library fixtures enable capture through the builder. Agent fixtures use the property.
  // Library-autoconfigure fixtures override this method because they also use the property.
  protected boolean captureExperimentalSpanAttributes() {
    return !(getTesting() instanceof AgentInstrumentationExtension) || EXPERIMENTAL_ATTRIBUTES;
  }

  protected final String experimental(String value) {
    return captureExperimentalSpanAttributes() ? value : null;
  }
}
