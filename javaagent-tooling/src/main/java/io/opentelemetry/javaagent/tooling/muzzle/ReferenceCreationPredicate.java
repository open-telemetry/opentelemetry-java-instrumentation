/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

/**
 * Defines a set of packages for which we'll create references.
 *
 * <p>For now we're hardcoding this to the instrumentation and javaagent-tooling packages so we only
 * create references from the method advice and helper classes.
 */
final class ReferenceCreationPredicate {
  // non-shaded packages
  private static final String AUTO_INSTRUMENTATION_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.";
  private static final String JAVA_AGENT_TOOLING_PACKAGE = "io.opentelemetry.javaagent.tooling.";
  private static final String AUTO_INSTRUMENTATION_API_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.api.";

  // shaded packages
  private static final String LIBRARY_INSTRUMENTATION_PACKAGE = "io.opentelemetry.instrumentation.";
  private static final String INSTRUMENTATION_API_PACKAGE = "io.opentelemetry.instrumentation.api.";

  static boolean shouldCreateReferenceFor(String className) {
    if (className.startsWith(INSTRUMENTATION_API_PACKAGE)
        || className.startsWith(AUTO_INSTRUMENTATION_API_PACKAGE)) {
      return false;
    }
    return className.startsWith(AUTO_INSTRUMENTATION_PACKAGE)
        || className.startsWith(JAVA_AGENT_TOOLING_PACKAGE)
        || className.startsWith(LIBRARY_INSTRUMENTATION_PACKAGE);
  }

  private ReferenceCreationPredicate() {}
}
