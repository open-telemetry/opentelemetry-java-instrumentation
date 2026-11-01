/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

public final class AwsSdkTestUtil {
  public static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes");

  public static String experimental(String value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }

  private AwsSdkTestUtil() {}
}
