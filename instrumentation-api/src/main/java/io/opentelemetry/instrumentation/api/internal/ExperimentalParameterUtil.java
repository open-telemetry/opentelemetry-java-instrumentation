/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ExperimentalParameterUtil {

  private static boolean redactQueryParameters;

  private ExperimentalParameterUtil() {}

  public static boolean isRedactQueryParameters() {
    return redactQueryParameters;
  }

  public static void setRedactQueryParameters(boolean redactQueryParameters) {
    ExperimentalParameterUtil.redactQueryParameters = redactQueryParameters;
  }
}
