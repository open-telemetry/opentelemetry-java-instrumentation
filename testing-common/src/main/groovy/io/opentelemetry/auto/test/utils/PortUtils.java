/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  public static void waitForPortToOpen(
      int port, long timeout, TimeUnit unit, Process process) {
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
