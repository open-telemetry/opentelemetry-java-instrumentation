/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public abstract class AbstractAwsSdkTest {
  protected abstract InstrumentationExtension testing();

  // Plain library fixtures enable capture through the builder.
  // Agent and library-autoconfigure fixtures override this to use the instrumentation property.
  protected boolean captureExperimentalSpanAttributes() {
    return Boolean.getBoolean("test.captureExperimentalSpanAttributes");
  }

  protected final String experimental(String value) {
    return captureExperimentalSpanAttributes() ? value : null;
  }
}
