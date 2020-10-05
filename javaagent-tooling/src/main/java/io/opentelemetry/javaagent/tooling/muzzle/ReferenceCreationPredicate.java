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
  private static final String REFERENCE_CREATION_PACKAGE = "io.opentelemetry.instrumentation.";

  private static final String JAVA_AGENT_PACKAGE = "io.opentelemetry.javaagent.tooling.";

  private static final String[] REFERENCE_CREATION_PACKAGE_EXCLUDES = {
    "io.opentelemetry.instrumentation.api.", "io.opentelemetry.instrumentation.auto.api."
  };

  static boolean shouldCreateReferenceFor(String className) {
    if (!className.startsWith(REFERENCE_CREATION_PACKAGE)) {
      return className.startsWith(JAVA_AGENT_PACKAGE);
    }
    for (String exclude : REFERENCE_CREATION_PACKAGE_EXCLUDES) {
      if (className.startsWith(exclude)) {
        return false;
      }
    }
    return true;
  }

  private ReferenceCreationPredicate() {}
}
