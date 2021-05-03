/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.util.function.Predicate;

public final class InstrumentationClassPredicate {
  // javaagent instrumentation packages
  private static final String JAVAAGENT_INSTRUMENTATION_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.";
  private static final String JAVAAGENT_API_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.api.";

  // library instrumentation packages (both shaded in the agent)
  private static final String LIBRARY_INSTRUMENTATION_PACKAGE = "io.opentelemetry.instrumentation.";
  private static final String INSTRUMENTATION_API_PACKAGE = "io.opentelemetry.instrumentation.api.";

  private final Predicate<String> additionalLibraryInstrumentationPredicate;

  public InstrumentationClassPredicate(
      Predicate<String> additionalLibraryInstrumentationPredicate) {
    this.additionalLibraryInstrumentationPredicate = additionalLibraryInstrumentationPredicate;
  }

  /**
   * Defines which classes are treated by muzzle as "internal", "helper" instrumentation classes.
   *
   * <p>This set of classes is defined by a package naming convention: all javaagent and library
   * instrumentation classes are treated as "helper" classes and are subjected to the reference
   * collection process. All others (including {@code instrumentation-api} and {@code javaagent-api}
   * modules are not scanned for references (but references to them are collected).
   *
   * <p>Aside from "standard" instrumentation helper class packages, instrumentation modules can
   * pass an additional predicate to include instrumentation helper classes from 3rd party packages.
   */
  public boolean isInstrumentationClass(String className) {
    return isJavaagentInstrumentationClass(className)
        || isLibraryInstrumentationClass(className)
        || additionalLibraryInstrumentationPredicate.test(className);
  }

  public boolean isProvidedByLibrary(String className) {
    return !isInstrumentationClass(className) && !isProvidedByJavaagent(className);
  }

  private boolean isProvidedByJavaagent(String className) {
    return className.startsWith(JAVAAGENT_API_PACKAGE)
        || className.startsWith(INSTRUMENTATION_API_PACKAGE)
        || className.startsWith("io.opentelemetry.javaagent.bootstrap.")
        || className.startsWith("io.opentelemetry.api.")
        || className.startsWith("io.opentelemetry.context.")
        || className.startsWith("io.opentelemetry.semconv.")
        || className.startsWith("org.slf4j.");
  }

  private static boolean isJavaagentInstrumentationClass(String className) {
    return className.startsWith(JAVAAGENT_INSTRUMENTATION_PACKAGE)
        && !className.startsWith(JAVAAGENT_API_PACKAGE);
  }

  private static boolean isLibraryInstrumentationClass(String className) {
    return className.startsWith(LIBRARY_INSTRUMENTATION_PACKAGE)
        && !className.startsWith(INSTRUMENTATION_API_PACKAGE);
  }
}
