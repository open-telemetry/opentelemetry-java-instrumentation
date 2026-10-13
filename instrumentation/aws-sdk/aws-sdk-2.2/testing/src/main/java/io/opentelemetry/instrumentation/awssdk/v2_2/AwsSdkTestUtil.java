/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

public final class AwsSdkTestUtil {
  public static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes");

  private AwsSdkTestUtil() {}
}
