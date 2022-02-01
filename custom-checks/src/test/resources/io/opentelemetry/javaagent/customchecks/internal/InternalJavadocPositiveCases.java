/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks.internal;

// BUG: Diagnostic contains: doesn't end with the javadoc disclaimer
public class InternalJavadocPositiveCases {

  // BUG: Diagnostic contains: doesn't end with the javadoc disclaimer
  public static class One {}

  /** Doesn't have the disclaimer. */
  // BUG: Diagnostic contains: doesn't end with the javadoc disclaimer
  public static class Two {}
}
