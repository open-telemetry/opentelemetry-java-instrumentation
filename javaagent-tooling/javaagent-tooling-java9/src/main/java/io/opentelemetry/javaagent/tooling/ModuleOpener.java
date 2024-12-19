/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class ModuleOpener {

  private static final Logger logger = Logger.getLogger(ModuleOpener.class.getName());

  private ModuleOpener() {}

  /**
   * Opens JPMS module to a class loader unnamed module
   *
   * @param classFromTargetModule class from target module
   * @param openTo class loader to open module for
   * @param packagesToOpen packages to open
   */
  public static void open(
      Instrumentation instrumentation,
      Class<?> classFromTargetModule,
      ClassLoader openTo,
      Collection<String> packagesToOpen) {

    Module targetModule = classFromTargetModule.getModule();
    Module openToModule = openTo.getUnnamedModule();
    Set<Module> openToModuleSet = Collections.singleton(openToModule);
    Map<String, Set<Module>> missingOpens = new HashMap<>();
    for (String packageName : packagesToOpen) {
      if (!targetModule.isOpen(packageName, openToModule)) {
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

    if (!instrumentation.isModifiableModule(targetModule)) {
      logger.log(WARNING, "Module '{0}' can't be modified", targetModule);
      return;
    }

    try {
      instrumentation.redefineModule(
          targetModule,
          Collections.<Module>emptySet(), // reads
          Collections.<String, Set<Module>>emptyMap(), // exports
          missingOpens, // opens
          Collections.<Class<?>>emptySet(), // uses
          Collections.<Class<?>, List<Class<?>>>emptyMap() // provides
          );
    } catch (Exception e) {
      logger.log(WARNING, "unable to redefine module", e);
    }
  }
}
