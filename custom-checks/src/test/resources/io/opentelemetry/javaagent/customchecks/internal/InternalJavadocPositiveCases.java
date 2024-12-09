/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks.internal;

// BUG: Diagnostic contains: doesn't end with any of the applicable javadoc disclaimers
public class InternalJavadocPositiveCases {

  // BUG: Diagnostic contains: doesn't end with any of the applicable javadoc disclaimers
  public static class One {}

  /** Doesn't have the disclaimer. */
  // BUG: Diagnostic contains: doesn't end with any of the applicable javadoc disclaimers
  public static class Two {}
}
