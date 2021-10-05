/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

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
public final class BootstrapPackagePrefixesHolder {

  private static volatile List<String> bootstrapPackagePrefixes;

  public static List<String> getBoostrapPackagePrefixes() {
    return bootstrapPackagePrefixes;
  }

  public static void setBoostrapPackagePrefixes(List<String> prefixes) {
    if (bootstrapPackagePrefixes != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    bootstrapPackagePrefixes = Collections.unmodifiableList(prefixes);
  }

  private BootstrapPackagePrefixesHolder() {}
}
