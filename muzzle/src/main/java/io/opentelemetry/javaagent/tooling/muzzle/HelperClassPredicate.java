/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.function.Predicate;

public final class HelperClassPredicate {
  // javaagent instrumentation packages
  private static final String JAVAAGENT_INSTRUMENTATION_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.";
  private static final String JAVAAGENT_API_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.api.";

  // library instrumentation packages (both shaded in the agent)
  private static final String LIBRARY_INSTRUMENTATION_PACKAGE = "io.opentelemetry.instrumentation.";
  private static final String INSTRUMENTATION_API_PACKAGE = "io.opentelemetry.instrumentation.api.";
  private static final String INSTRUMENTATION_INTERNAL_API_PACKAGE =
      "io.opentelemetry.instrumentation.appender.api.internal";

  private final Predicate<String> additionalLibraryHelperClassPredicate;

  public HelperClassPredicate(Predicate<String> additionalLibraryHelperClassPredicate) {
    this.additionalLibraryHelperClassPredicate = additionalLibraryHelperClassPredicate;
  }

  /**
   * Defines which classes are treated by muzzle as "internal", "helper" instrumentation classes.
   *
   * <p>This set of classes is defined by a package naming convention: all javaagent and library
   * instrumentation classes are treated as "helper" classes and are subjected to the reference
   * collection process. All others (including {@code instrumentation-api} and {@code
   * javaagent-instrumentation-api} modules are not scanned for references (but references to them
   * are collected).
   *
   * <p>Aside from "standard" instrumentation helper class packages, instrumentation modules can
   * pass an additional predicate to include instrumentation helper classes from 3rd party packages.
   */
  public boolean isHelperClass(String className) {
    return isJavaagentHelperClass(className)
        || isLibraryHelperClass(className)
        || additionalLibraryHelperClassPredicate.test(className);
  }

  public boolean isLibraryClass(String className) {
    return !isHelperClass(className) && !isBootstrapClass(className);
  }

  private static boolean isBootstrapClass(String className) {
    return className.startsWith(JAVAAGENT_API_PACKAGE)
        || className.startsWith(INSTRUMENTATION_API_PACKAGE)
        || className.startsWith(INSTRUMENTATION_INTERNAL_API_PACKAGE)
        || className.startsWith("io.opentelemetry.javaagent.bootstrap.")
        || className.startsWith("io.opentelemetry.api.")
        || className.startsWith("io.opentelemetry.context.")
        || className.startsWith("io.opentelemetry.semconv.")
        || className.startsWith("org.slf4j.");
  }

  private static boolean isJavaagentHelperClass(String className) {
    return className.startsWith(JAVAAGENT_INSTRUMENTATION_PACKAGE)
        && !className.startsWith(JAVAAGENT_API_PACKAGE);
  }

  private static boolean isLibraryHelperClass(String className) {
    return className.startsWith(LIBRARY_INSTRUMENTATION_PACKAGE)
        && !className.startsWith(INSTRUMENTATION_API_PACKAGE)
        && !className.startsWith(INSTRUMENTATION_INTERNAL_API_PACKAGE);
  }
}
