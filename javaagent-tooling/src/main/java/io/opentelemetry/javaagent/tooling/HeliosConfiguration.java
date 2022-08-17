/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

public class HeliosConfiguration {
  public static final String HELIOS_TEST_TRIGGERED_TRACE = "hs-triggered-test";

  public static boolean isHsDebugEnabled() {
    String heliosDebugProp = System.getenv("HS_DEBUG");

    return heliosDebugProp != null && heliosDebugProp.equalsIgnoreCase("true");
  }
}
