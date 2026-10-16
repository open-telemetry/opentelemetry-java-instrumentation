/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTestUtil.EXPERIMENTAL_ATTRIBUTES;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public abstract class AbstractAwsSdkTest {
  protected abstract InstrumentationExtension testing();

  // Plain library fixtures enable capture through the builder. Agent fixtures use the property.
  // Library-autoconfigure fixtures override this method because they also use the property.
  protected boolean captureExperimentalSpanAttributes() {
    return EXPERIMENTAL_ATTRIBUTES;
  }

  protected final String experimental(String value) {
    return captureExperimentalSpanAttributes() ? value : null;
  }
}
