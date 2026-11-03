/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public abstract class AbstractAwsSdkTest {
  protected static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes");

  protected abstract InstrumentationExtension getTesting();

  // Plain library fixtures enable capture through the builder.
  // Agent and library-autoconfigure fixtures override this to use the instrumentation property.
  protected boolean captureExperimentalSpanAttributes() {
    return Boolean.getBoolean("test.captureExperimentalSpanAttributes");
  }

  protected final String experimental(String value) {
    return captureExperimentalSpanAttributes() ? value : null;
  }
}
