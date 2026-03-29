/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ProcessArguments {

  public static String[] getProcessArguments() {
    // note: ProcessHandle arguments() always returns null on Windows
    //       https://bugs.openjdk.org/browse/JDK-8176725
    return ProcessHandle.current().info().arguments().orElseGet(() -> new String[0]);
  }

  private ProcessArguments() {}
}
