/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks.internal;

// BUG: Diagnostic contains: missing the standard internal javadoc
public class InternalJavadocPositiveCases {

  // BUG: Diagnostic contains: missing the standard internal javadoc
  public static class One {}

  /** Doesn't have the disclaimer. */
  // BUG: Diagnostic contains: missing the standard internal javadoc
  public static class Two {}
}
