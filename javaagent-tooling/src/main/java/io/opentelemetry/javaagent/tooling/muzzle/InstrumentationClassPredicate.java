/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

public final class InstrumentationClassPredicate {
  // non-shaded packages
  private static final String AUTO_INSTRUMENTATION_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.";
  private static final String JAVA_AGENT_TOOLING_PACKAGE = "io.opentelemetry.javaagent.tooling.";
  private static final String AUTO_INSTRUMENTATION_API_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.api.";

  // shaded packages
  private static final String LIBRARY_INSTRUMENTATION_PACKAGE = "io.opentelemetry.instrumentation.";
  private static final String INSTRUMENTATION_API_PACKAGE = "io.opentelemetry.instrumentation.api.";

  /**
   * Defines which classes are treated by muzzle as "internal", "helper" instrumentation classes.
   *
   * <p>This set of classes is defined by a package naming convention: all automatic and manual
   * instrumentation classes and {@code javaagent.tooling} classes are treated as "helper" classes
   * and are subjected to the reference collection process. All others (including {@code
   * instrumentation-api} and {@code javaagent-api} modules are not scanned for referenced (but
   * references to them are collected).
   */
  public static boolean isInstrumentationClass(String className) {
    if (className.startsWith(INSTRUMENTATION_API_PACKAGE)
        || className.startsWith(AUTO_INSTRUMENTATION_API_PACKAGE)) {
      return false;
    }
    return className.startsWith(AUTO_INSTRUMENTATION_PACKAGE)
        || className.startsWith(JAVA_AGENT_TOOLING_PACKAGE)
        || className.startsWith(LIBRARY_INSTRUMENTATION_PACKAGE);
  }

  private InstrumentationClassPredicate() {}
}
