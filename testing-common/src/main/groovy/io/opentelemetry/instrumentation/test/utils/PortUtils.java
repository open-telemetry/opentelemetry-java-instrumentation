/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.auto.test.utils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class PortUtils {

  public static int UNUSABLE_PORT = 61;

  /** Open up a random, reusable port. */
  public static int randomOpenPort() {
    ServerSocket socket;
    try {
      socket = new ServerSocket(0);
      socket.setReuseAddress(true);
      socket.close();
      return socket.getLocalPort();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      return -1;
    }
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
        throw new RuntimeException("Interrupted while waiting for " + port + " to be opened");
      }
    }

    throw new RuntimeException("Timed out waiting for port " + port + " to be opened");
  }

  public static void waitForPortToOpen(int port, long timeout, TimeUnit unit, Process process) {
    long waitUntil = System.currentTimeMillis() + unit.toMillis(timeout);

    while (System.currentTimeMillis() < waitUntil) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while waiting for " + port + " to be opened");
      }

      // Note: we should have used `process.isAlive()` here but it is java8 only
      try {
        process.exitValue();
        throw new RuntimeException("Process died before port " + port + " was opened");
      } catch (IllegalThreadStateException e) {
        // process is still alive, things are good.
      }

      if (isPortOpen(port)) {
        return;
      }
    }

    throw new RuntimeException("Timed out waiting for port " + port + " to be opened");
  }
}
