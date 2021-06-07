/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

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

  private static boolean isPortOpen(int port) {
    try (Socket socket = new Socket((String) null, port)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public static void waitForPortToOpen(int port, long timeout, TimeUnit unit) {
    long waitUntil = System.currentTimeMillis() + unit.toMillis(timeout);

    while (System.currentTimeMillis() < waitUntil) {
      if (isPortOpen(port)) {
        return;
      }

      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for " + port + " to be opened");
      }
    }

    throw new IllegalStateException("Timed out waiting for port " + port + " to be opened");
  }

  public static void waitForPortToOpen(int port, long timeout, TimeUnit unit, Process process) {
    long waitUntil = System.currentTimeMillis() + unit.toMillis(timeout);

    while (System.currentTimeMillis() < waitUntil) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new IllegalStateException("Interrupted while waiting for " + port + " to be opened");
      }

      // Note: we should have used `process.isAlive()` here but it is java8 only
      try {
        process.exitValue();
        throw new IllegalStateException("Process died before port " + port + " was opened");
      } catch (IllegalThreadStateException e) {
        // process is still alive, things are good.
      }

      if (isPortOpen(port)) {
        return;
      }
    }

    throw new IllegalStateException("Timed out waiting for port " + port + " to be opened");
  }

  private PortUtils() {}
}
