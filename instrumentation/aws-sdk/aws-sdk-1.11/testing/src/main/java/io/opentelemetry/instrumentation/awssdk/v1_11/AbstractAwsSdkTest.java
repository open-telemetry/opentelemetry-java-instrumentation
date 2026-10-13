/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

public abstract class AbstractAwsSdkTest {
  protected boolean captureExperimentalSpanAttributes() {
    return Boolean.getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes");
  }

  protected final String experimental(String value) {
    return captureExperimentalSpanAttributes() ? value : null;
  }
}
