/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

/**
 * {@link BootstrapPackagePrefixesHolder} is an utility class that holds package prefixes. The
 * classes from these packages are pushed to the bootstrap classloader.
 *
 * <p>The prefixes are loaded by {@code AgentInstaller} and consumed by classloader instrumentation.
 * The instrumentation does not have access to the installer, therefore this utility class is used
 * to share package prefixes.
 */
public class BootstrapPackagePrefixesHolder {

  private static String[] bootstrapPrefixes;

  public static String[] getBootstrapPrefixes() {
    return bootstrapPrefixes;
  }

  public static void setBootstrapPrefixes(String[] prefixes) {
    bootstrapPrefixes = prefixes;
  }
}
