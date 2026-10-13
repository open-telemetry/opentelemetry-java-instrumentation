/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTestUtil.EXPERIMENTAL_ATTRIBUTES;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public abstract class AbstractAwsSdkTest {
  protected abstract InstrumentationExtension testing();

  protected boolean captureExperimentalSpanAttributes() {
    return !(testing() instanceof AgentInstrumentationExtension) || EXPERIMENTAL_ATTRIBUTES;
  }

  protected final String experimental(String value) {
    return captureExperimentalSpanAttributes() ? value : null;
  }
}
