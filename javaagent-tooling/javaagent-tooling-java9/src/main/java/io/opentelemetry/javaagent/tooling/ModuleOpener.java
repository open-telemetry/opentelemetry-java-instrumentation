/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.description.type.PackageDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/**
 * Module opener provides ability to open JPMS modules and allows instrumentation classloader to
 * access module contents without requiring JVM arguments modification. <br>
 * Usage of this class must be guarded with an {@code net.bytebuddy.utility.JavaModule#isSupported}
 * check as it's compiled for Java 9+, otherwise an {@link UnsupportedClassVersionError} will be
 * thrown for java 8.
 */
public class ModuleOpener {

  private static final Logger logger = Logger.getLogger(ModuleOpener.class.getName());

  // AgentClassLoader is in unnamed module of the bootstrap loader
  private static final JavaModule UNNAMED_BOOT_MODULE = JavaModule.ofType(AgentClassLoader.class);

  private ModuleOpener() {}

  /**
   * Opens JPMS module to a class loader unnamed module
   *
   * @param targetModule target module
   * @param openTo class loader to open module for, {@literal null} to use the unnamed module of
   *     bootstrap classloader.
   * @param packagesToOpen packages to open
   */
  public static void open(
      Instrumentation instrumentation,
      JavaModule targetModule,
      @Nullable ClassLoader openTo,
      Collection<String> packagesToOpen) {

    JavaModule openToModule =
        openTo != null ? JavaModule.of(openTo.getUnnamedModule()) : UNNAMED_BOOT_MODULE;
    Set<JavaModule> openToModuleSet = Collections.singleton(openToModule);
    Map<String, Set<JavaModule>> missingOpens = new HashMap<>();
    for (String packageName : packagesToOpen) {
      if (!targetModule.isOpened(new PackageDescription.Simple(packageName), openToModule)) {
        missingOpens.put(packageName, openToModuleSet);
        logger.log(
            FINE,
            "Exposing package '{0}' in module '{1}' to module '{2}'",
            new Object[] {packageName, targetModule, openToModule});
      }
    }
    if (missingOpens.isEmpty()) {
      return;
    }

    try {
      ClassInjector.UsingInstrumentation.redefineModule(
          instrumentation,
          targetModule,
          Collections.emptySet(),
          Collections.emptyMap(),
          missingOpens,
          Collections.emptySet(),
          Collections.emptyMap());
    } catch (Exception e) {
      logger.log(WARNING, "Failed to redefine module '" + targetModule.getActualName() + "'", e);
    }
  }
}
