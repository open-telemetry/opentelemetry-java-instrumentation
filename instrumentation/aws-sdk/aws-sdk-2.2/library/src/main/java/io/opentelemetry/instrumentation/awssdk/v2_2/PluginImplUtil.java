/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.util.logging.Level;
import java.util.logging.Logger;

final class PluginImplUtil {
  private PluginImplUtil() {}

  private static final Logger logger = Logger.getLogger(PluginImplUtil.class.getName());

  /**
   * Check if the given {@code moduleNameImpl} is present.
   *
   * <p>For library instrumentations, the Impls will always be available but might fail to load if
   * the corresponding SDK classes are not on the class path. For javaagent, the Impl is available
   * only when the corresponding InstrumentationModule was successfully applied (muzzle passed).
   *
   * <p>Note that an present-but-incompatible library can only be detected by Muzzle. In
   * library-mode, users need to ensure they are using a compatible SDK (component) versions
   * themselves.
   *
   * @param implSimpleClassName The simple name of the impl class, e.g. {@code "SqsImpl"}. *
   */
  static boolean isImplPresent(String implSimpleClassName) {
    // We use getName().replace() instead of getPackage() because the latter is not guaranteed to
    // work in all cases (e.g., we might be loaded into a custom classloader that doesn't handle it)
    String implFullClassName =
        PluginImplUtil.class.getName().replace(".PluginImplUtil", "." + implSimpleClassName);
    try {
      // using package name here because library instrumentation classes are relocated when embedded
      // in the agent
      Class.forName(implFullClassName);
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      // ClassNotFoundException will happen when muzzle disabled us in javaagent mode; LinkageError
      // (most likely NoClassDefFoundError) should happen when the class is loaded in library mode
      // (where it is always available) but a dependency failed to load (most likely because the
      // corresponding SDK dependency is not on the class path).
      logger.log(
          Level.FINE, e, () -> implFullClassName + " not present, probably incompatible version");
      return false;
    }
  }
}
