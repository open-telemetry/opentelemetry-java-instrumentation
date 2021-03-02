/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.internal;

import java.util.Collections;
import java.util.List;

/**
 * {@link BootstrapPackagePrefixesHolder} is an utility class that holds package prefixes. The
 * classes from these packages are pushed to the bootstrap classloader.
 *
 * <p>The prefixes are loaded by {@code AgentInstaller} and consumed by classloader instrumentation.
 * The instrumentation does not have access to the installer, therefore this utility class is used
 * to share package prefixes.
 */
public class BootstrapPackagePrefixesHolder {

  private static volatile List<String> BOOSTRAP_PACKAGE_PREFIXES;

  public static List<String> getBoostrapPackagePrefixes() {
    return BOOSTRAP_PACKAGE_PREFIXES;
  }

  public static void setBoostrapPackagePrefixes(List<String> prefixes) {
    if (BOOSTRAP_PACKAGE_PREFIXES != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    BOOSTRAP_PACKAGE_PREFIXES = Collections.unmodifiableList(prefixes);
  }
}
