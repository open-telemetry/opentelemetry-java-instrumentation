/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

public final class PortUtils {

  public static final int UNUSABLE_PORT = 61;

  private static final PortAllocator portAllocator = new PortAllocator();

  /** Find consecutive open ports, returning the first one in the range. */
  public static int findOpenPorts(int count) {
    return portAllocator.getPorts(count);
  }

  /** Find open port. */
  public static int findOpenPort() {
    return portAllocator.getPort();
  }

  private PortUtils() {}
}
